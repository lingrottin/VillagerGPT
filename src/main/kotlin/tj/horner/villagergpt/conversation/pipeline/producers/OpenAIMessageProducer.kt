package tj.horner.villagergpt.conversation.pipeline.producers

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import org.bukkit.configuration.Configuration
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.ConversationMessageProducer

class OpenAIMessageProducer(config: Configuration) : ConversationMessageProducer {
    private val defaultEndpoint = "https://api.openai.com/v1/"

    private val openAI = OpenAI(
        OpenAIConfig(
            config.getString("openai-key")!!,
            LogLevel.None,
            host=OpenAIHost(config.getString("openai-endpoint") ?: defaultEndpoint)
        )
    )

    private val model = ModelId(config.getString("openai-model") ?: "gpt-3.5-turbo")

    @OptIn(BetaOpenAI::class)
    override suspend fun produceNextMessage(conversation: VillagerConversation): String {
        val request = ChatCompletionRequest(
            model = model,
            messages = conversation.messages,
            temperature = 0.7,
            user = conversation.player.uniqueId.toString()
        )

        val completion = openAI.chatCompletion(request)
        return completion.choices[0].message!!.content
    }
}