package tv.parentapproved.app.util

object PlaylistUrlParser {

    private val PLAYLIST_ID_REGEX = Regex("^PL[a-zA-Z0-9_-]+$")
    private val URL_LIST_PARAM_REGEX = Regex("[?&]list=([a-zA-Z0-9_-]+)")

    fun parse(input: String): String? {
        val trimmed = input.trim()

        // Bare playlist ID
        if (PLAYLIST_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        // URL with list= parameter
        if (trimmed.contains("youtube.com/") || trimmed.contains("youtu.be/")) {
            val match = URL_LIST_PARAM_REGEX.find(trimmed)
            if (match != null) {
                val id = match.groupValues[1]
                if (id.startsWith("PL")) return id
            }
            return null
        }

        return null
    }
}
