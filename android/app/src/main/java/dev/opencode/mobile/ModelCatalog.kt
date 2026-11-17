package dev.opencode.mobile

import java.util.Locale

internal data class ModelChoice(val main: ChatModel, val fastModel: ChatModel? = null) {
    val name: String get() = if (main.isFastModel) main.name.replace(Regex("(?i) +fast$"), "") else main.name
    fun contains(key: String?): Boolean = main.key == key || fastModel?.key?.let { it == key } == true
    fun supportsFast(model: ChatModel): Boolean = fastModel != null || (!model.isFastModel && model.fastVariant != null)
    fun isFast(model: ChatModel, variant: String?): Boolean = model.isFastModel ||
        (variant != null && variant == model.fastVariant)

    fun toggleFast(model: ChatModel, variant: String?): Pair<ChatModel, String?> {
        fastModel?.let { alternate ->
            val target = if (model.key == alternate.key) main else alternate
            return target to variant?.takeIf { it in target.variants && it != model.fastVariant }
        }
        return model to model.fastVariant?.takeUnless { it == variant }
    }
}

internal val ChatModel.fastVariant: String? get() = variants.firstOrNull { it.equals("fast", ignoreCase = true) }
internal val ChatModel.isFastModel: Boolean get() = id.endsWith("-fast", ignoreCase = true) &&
    (name.endsWith(" Fast", ignoreCase = true) || upstreamId == id.dropLast(5))

internal fun List<ChatModel>.modelChoices(): List<ModelChoice> {
    val byKey = associateBy(ChatModel::key)
    val fastModels = filter { fast ->
        if (!fast.isFastModel || fast.upstreamId == null) return@filter false
        val main = byKey["${fast.provider}/${fast.id.dropLast(5)}"]
        main != null && main.upstreamId == fast.upstreamId
    }.associateBy { "${it.provider}/${it.id.dropLast(5)}" }
    val nestedKeys = fastModels.values.mapTo(mutableSetOf(), ChatModel::key)
    return filterNot { it.key in nestedKeys }.map { ModelChoice(it, fastModels[it.key]) }
}

internal fun providerLabel(id: String): String = when (id.lowercase(Locale.ROOT)) {
    "opencode" -> "OpenCode"
    "openai" -> "OpenAI"
    "openrouter" -> "OpenRouter"
    "github-copilot" -> "GitHub Copilot"
    "google" -> "Google"
    "google-vertex" -> "Google Vertex AI"
    "azure" -> "Azure"
    "amazon-bedrock" -> "Amazon Bedrock"
    "xai" -> "xAI"
    "deepseek" -> "DeepSeek"
    else -> id.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
