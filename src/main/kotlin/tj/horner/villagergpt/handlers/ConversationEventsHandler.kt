package tj.horner.villagergpt.handlers

import com.aallam.openai.api.BetaOpenAI
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import tj.horner.villagergpt.MetadataKey
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.chat.ChatMessageTemplate
import tj.horner.villagergpt.conversation.formatting.MessageFormatter
import tj.horner.villagergpt.events.VillagerConversationEndEvent
import tj.horner.villagergpt.events.VillagerConversationMessageEvent
import tj.horner.villagergpt.events.VillagerConversationStartEvent

class ConversationEventsHandler(private val plugin: VillagerGPT) : Listener {
    @EventHandler
    fun onConversationStart(evt: VillagerConversationStartEvent) {
        val message = Component.text("你正在与")
            .append(evt.conversation.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text("交谈。发送聊天信息以开始对话，使用 /ttvend 以结束对话。"))
            .decorate(TextDecoration.ITALIC)

        evt.conversation.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        evt.conversation.villager.isAware = false
        evt.conversation.villager.lookAt(evt.conversation.player)

        plugin.logger.info("Conversation started between ${evt.conversation.player.name} and ${evt.conversation.villager.name}")
    }

    @EventHandler
    fun onConversationEnd(evt: VillagerConversationEndEvent) {
        val message = Component.text("与")
            .append(evt.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text("的对话结束。"))
            .decorate(TextDecoration.ITALIC)

        evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        evt.villager.resetOffers()
        evt.villager.isAware = true

        plugin.logger.info("Conversation ended between ${evt.player.name} and ${evt.villager.name}")
    }

    @EventHandler
    fun onVillagerInteracted(evt: PlayerInteractEntityEvent) {
        if (evt.rightClicked !is Villager) return
        val villager = evt.rightClicked as Villager

        // Villager is in a conversation with another player
        val existingConversation = plugin.conversationManager.getConversation(villager)
        if (existingConversation != null && existingConversation.player.uniqueId != evt.player.uniqueId) {
            val message = Component.text("这个村民正在和[")
                .append(existingConversation.player.displayName())
                .append(Component.text("]交谈。请稍后再试。"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            evt.isCancelled = true
            return
        }

        if (!evt.player.hasMetadata(MetadataKey.SelectingVillager)) return

        // Player is selecting a villager for conversation
        evt.isCancelled = true

        if (villager.profession == Villager.Profession.NONE) {
            val message = Component.text("这个村民没有职业，无法与之交谈。")
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        plugin.conversationManager.startConversation(evt.player, villager)
        evt.player.removeMetadata(MetadataKey.SelectingVillager, plugin)
    }

    @EventHandler
    suspend fun onSendMessage(evt: AsyncChatEvent) {
        val conversation = plugin.conversationManager.getConversation(evt.player) ?: return
        evt.isCancelled = true

        if (conversation.pendingResponse) {
            val message = Component.text("请耐心等待")
                .append(conversation.villager.name().color(NamedTextColor.AQUA))
                .append(Component.text("的回复。"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        conversation.pendingResponse = true
        val villager = conversation.villager

        try {
            val pipeline = plugin.messagePipeline

            val playerMessage = PlainTextComponentSerializer.plainText().serialize(evt.originalMessage())
            val formattedPlayerMessage = MessageFormatter.formatMessageFromPlayer(Component.text(playerMessage), villager)

            evt.player.sendMessage(formattedPlayerMessage)

            val actions = pipeline.run(playerMessage, conversation)
            if (!conversation.ended) {
                withContext(plugin.minecraftDispatcher) {
                    actions.forEach { it.run() }
                }
            }
        } catch(e: Exception) {
            val message = Component.text("在获取")
                .append(villager.name().color(NamedTextColor.AQUA))
                .append(Component.text("的回复时发生了错误，请稍后再试。"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            throw(e)
        } finally {
            conversation.pendingResponse = false
        }
    }

    @OptIn(BetaOpenAI::class)
    @EventHandler
    fun onConversationMessage(evt: VillagerConversationMessageEvent) {
        if (!plugin.config.getBoolean("log-conversations")) return
        plugin.logger.info("Message between ${evt.conversation.player.name} and ${evt.conversation.villager.name}: ${evt.message}")
    }

    @EventHandler
    fun onVillagerDied(evt: EntityDeathEvent) {
        if (evt.entity !is Villager) return
        val villager = evt.entity as Villager

        val conversation = plugin.conversationManager.getConversation(villager)
        if (conversation != null) {
            plugin.conversationManager.endConversation(conversation)
        }
    }
}