package tj.horner.villagergpt.commands

import com.github.shynixn.mccoroutine.bukkit.SuspendingCommandExecutor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import tj.horner.villagergpt.MetadataKey
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.chat.ChatMessageTemplate

class TalkCommand(private val plugin: VillagerGPT) : SuspendingCommandExecutor {
    override suspend fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) return true
        val player: Player = sender

        val conversation = plugin.conversationManager.getConversation(player)
        if (conversation != null) {
            val message = Component.text("你已经在和")
                .append(conversation.villager.name().color(NamedTextColor.AQUA))
                .append(Component.text("交谈了"))
                .decorate(TextDecoration.ITALIC)

            player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return true
        }

        val message = Component.text("对一个村民按下[")
            .append(Component.text("使用").color(NamedTextColor.AQUA))
            .append(Component.text("]以开始对话"))
            .decorate(TextDecoration.ITALIC)

        player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        player.setMetadata(MetadataKey.SelectingVillager, FixedMetadataValue(plugin, true))
        return true
    }
}