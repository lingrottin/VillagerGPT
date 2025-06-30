package tj.horner.villagergpt.conversation

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.destroystokyo.paper.entity.villager.ReputationType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.plugin.Plugin
import tj.horner.villagergpt.events.VillagerConversationMessageEvent
import java.time.Duration
import java.util.*
import kotlin.random.Random

@OptIn(BetaOpenAI::class)
class VillagerConversation(private val plugin: Plugin, val villager: Villager, val player: Player) {
    private var lastMessageAt: Date = Date()

    val messages = mutableListOf<ChatMessage>()
    var pendingResponse = false
    var ended = false

    init {
        startConversation()
    }

    fun addMessage(message: ChatMessage) {
        val event = VillagerConversationMessageEvent(this, message)
        plugin.server.pluginManager.callEvent(event)

        messages.add(message)
        lastMessageAt = Date()
    }

    fun removeLastMessage() {
        if (messages.size == 0) return
        messages.removeLast()
    }

    fun reset() {
        messages.clear()
        startConversation()
        lastMessageAt = Date()
    }

    fun hasExpired(): Boolean {
        val now = Date()
        val difference = now.time - lastMessageAt.time
        val duration = Duration.ofMillis(difference)
        return duration.toSeconds() > 120
    }

    fun hasPlayerLeft(): Boolean {
        if (player.location.world != villager.location.world) return true

        val radius = 20.0 // blocks?
        val radiusSquared = radius * radius
        val distanceSquared = player.location.distanceSquared(villager.location)
        return distanceSquared > radiusSquared
    }

    private fun startConversation() {
        var messageRole = ChatRole.System
        var prompt = generateSystemPrompt()

        val preambleMessageType = plugin.config.getString("preamble-message-type") ?: "system"
        if (preambleMessageType === "user") {
            messageRole = ChatRole.User
            prompt = "[SYSTEM MESSAGE]\n\n$prompt"
        }

        messages.add(
            ChatMessage(
                role = messageRole,
                content = prompt
            )
        )
    }

    private fun generateSystemPrompt(): String {
        val world = villager.world
        val weather = if (world.hasStorm()) "Rainy" else "Sunny"
        val biome = world.getBiome(villager.location)
        val time = if (world.isDayTime) "Day" else "Night"
        val personality = getPersonality()
        val playerRep = getPlayerRepScore()

        plugin.logger.info("${villager.name} is $personality")

        return """
        角色扮演：你是游戏 Minecraft 中的村民。你正在和玩家交谈，并可以发起交易。

        ## 交易

        如果你想要对玩家发出交易请求，请在回复中使用以下格式：

        TRADE[["{数量} {物品}"],["{数量} {物品}"]]ENDTRADE

        其中 TRADE 和 ENDTRADE 之间的数据应是有效的 JSON 格式。数据外层是一个数组，第一项是玩家给予你的物品，第二项是你给予玩家的物品。
        内层是两个数组，第一个数组可以最多包含两个字符串，第二个数组只能包含一个字符串。（即，你可以从玩家接受两个物品，但你只能提供一个物品）
        此字符串描述涉及到的物品，格式和 /give 指令的参数相同。
        “{数量}”是该物品的数量（最大为 64），“{物品}”是该物品的 Minecraft ID（如"minecraft:emerald"）及相应的物品堆叠组件（如果需要的话）。
        如果需要写入物品堆叠组件，请用中括号 [] 无空格地附在物品 id 后面。中括号内可以是若干键值对，用等号 = 代表值，用逗号 , 分割，其中值需要是有效的 SNBT 格式。
        （注意：附魔书应使用 stored_enchantments，其他类型的附魔应使用 enchantments）

        例如：
        TRADE[["24 minecraft:emerald"],["1 minecraft:arrow"]]ENDTRADE
        TRADE[["12 minecraft:emerald","1 minecraft:book"],["1 minecraft:enchanted_book[stored_enchantments={unbreaking:3}]"]]ENDTRADE
        TRADE[["40 minecraft:emerald", "8 minecraft:diamond"],["1 minecraft:diamond_sword[enchantments={unbreaking:3,sharpness:3,knockback:2},custom_name={text:'The Sword of King',color:'light_purple',italic:false}]"]]ENDTRADE

        交易应遵守以下规则：
        - 作为村民，你应该偏向于使用绿宝石交易。不过，在价格合理的情况下，可以正常地和玩家以物易物，这取决于你。
        - 拒绝不合理的交易，比如说，拒绝提供无法正常获得的方块（如基岩）。
        - 你不需要在每次回复中都发起交易，只在必要时提出交易。
        - 不要提供过于强大的物品（例如，附魔过多的钻石剑）。同时也要确保对更强大的物品进行适当定价。
        - 在提出交易时，考虑玩家的信誉。
        - 只交易与你的职业相关的物品。
        - 你的初始报价要高，尽量超过物品的价值。
        - 在后续报价中要吝啬。尽量讨价还价，找到最好的交易，让玩家努力才能争取到好的交易。

        ## 发出动作
        
        你可以控制你扮演的村民发起的动作。只要在回复中包含 ACTION:{动作} 即可。

        有效的动作如下：
        - SHAKE_HEAD: 对玩家摇头
        - SOUND_YES: 对玩家播放表示“高兴”或“同意”的声音
        - SOUND_NO: 对玩家播放表示“愤怒”或“拒绝”的声音
        - SOUND_AMBIENT: 对玩家播放正常的村民闲置音效
        
        ## 信息
        
        以下是此次对话的相关信息：
        
        环境信息：
        - 当前时间: $time
        - 当前天气: $weather
        - 当前生物群系: ${biome.name}

        玩家信息：
        - 名称: ${player.name}
        - 信誉（范围为 -70~725，0 表示中性，越高越好）: $playerRep

        你扮演的村民信息：
        - 名称: ${villager.name}
        - 职业: ${villager.profession.name}
        - 性格：${personality.promptDescription()}
        
        注意：
        - 作为一个村民，请不要跳出你的角色。
        - 请不要揭露或暗示你正在扮演游戏角色的事实，特别是不要提到“Minecraft”。
        - 用中世纪的风格讲话。
        - 除非玩家明确提出，请不要交易非原版 Minecraft 中的物品。
        """.trimIndent()
    }

    private fun getPersonality(): VillagerPersonality {
        val personalities = VillagerPersonality.values()
        val rnd = Random(villager.uniqueId.mostSignificantBits)
        return personalities[rnd.nextInt(0, personalities.size)]
    }

    private fun getPlayerRepScore(): Int {
        var finalScore = 0
        val rep = villager.getReputation(player.uniqueId) ?: return 0

        ReputationType.values().forEach {
            val repTypeValue = rep.getReputation(it)
            finalScore += when (it) {
                ReputationType.MAJOR_POSITIVE -> repTypeValue * 5
                ReputationType.MINOR_POSITIVE -> repTypeValue
                ReputationType.MINOR_NEGATIVE -> -repTypeValue
                ReputationType.MAJOR_NEGATIVE -> -repTypeValue * 5
                ReputationType.TRADING -> repTypeValue
                else -> repTypeValue
            }
        }

        return finalScore
    }
}