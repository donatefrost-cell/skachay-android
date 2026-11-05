package ru.skachay.app.download

data class ResolvedMedia(val url: String, val type: Kind, val headers: Map<String, String> = emptyMap()) {
    enum class Kind { IMAGE, VIDEO, AUDIO }
}

fun interface MediaResolver {
    fun resolve(url: String): List<ResolvedMedia>
}
