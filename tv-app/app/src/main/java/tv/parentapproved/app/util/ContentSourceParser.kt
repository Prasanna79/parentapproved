package tv.parentapproved.app.util

/**
 * Parses any YouTube URL (playlist, video, channel, show) into a ContentSource.
 * Replaces PlaylistUrlParser with source-agnostic parsing.
 */

enum class SourceType {
    YT_PLAYLIST, YT_VIDEO, YT_CHANNEL
}

data class ContentSource(
    val type: SourceType,
    val id: String,
    val canonicalUrl: String,
)

sealed class ParseResult {
    data class Success(val source: ContentSource) : ParseResult()
    data class Rejected(val message: String) : ParseResult()
}

object ContentSourceParser {

    private val BARE_PL_ID = Regex("^PL[a-zA-Z0-9_-]+$")
    private val LIST_PARAM = Regex("[?&]list=([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
    private val VIDEO_ID_PARAM = Regex("[?&]v=([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
    private val YOUTU_BE_PATH = Regex("^/([a-zA-Z0-9_-]+)")
    private val PATH_VIDEO = Regex("^/(?:shorts|embed|v|live)/([a-zA-Z0-9_-]+)")
    private val CHANNEL_UC = Regex("^/channel/(UC[a-zA-Z0-9_-]+)")
    private val HANDLE = Regex("^/@([a-zA-Z0-9_.\\-]+)")
    private val CUSTOM_CHANNEL = Regex("^/(c|user)/([a-zA-Z0-9_.\\-]+)")
    private val SHOW_PATH = Regex("^/show/VL(PL[a-zA-Z0-9_-]+)")
    private val FILE_EXT = Regex("\\.(mp4|webm|mp3|mkv|avi|mov|flv|ogg|wav)$", RegexOption.IGNORE_CASE)
    private val PRIVATE_IP = Regex(
        "^https?://(localhost|0\\.0\\.0\\.0|127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|\\[::1\\])"
    )
    private val YOUTUBE_DOMAINS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    // Reject non-PL list params (mixes, uploads, liked, watch later)
    private val REJECT_LIST_PREFIXES = setOf("RD", "UU", "LL", "WL")

    fun parse(input: String): ParseResult {
        val trimmed = input.trim()

        // Empty check
        if (trimmed.isBlank()) {
            return ParseResult.Rejected("Please enter a YouTube URL")
        }

        // Bare PL ID
        if (BARE_PL_ID.matches(trimmed)) {
            return ParseResult.Success(ContentSource(
                type = SourceType.YT_PLAYLIST,
                id = trimmed,
                canonicalUrl = "https://www.youtube.com/playlist?list=$trimmed",
            ))
        }

        // Normalize: add scheme if missing
        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("www.") || trimmed.startsWith("m.") || trimmed.startsWith("youtube.com") || trimmed.startsWith("youtu.be") -> "https://$trimmed"
            else -> trimmed // not a URL
        }

        // Try to parse as URL
        val urlParts = parseUrl(normalized) ?: return ParseResult.Rejected("Not a valid YouTube URL")

        // Private IP block
        if (PRIVATE_IP.containsMatchIn(normalized)) {
            return ParseResult.Rejected("Local/private URLs are not supported")
        }

        // File extension block
        if (FILE_EXT.containsMatchIn(urlParts.path)) {
            return ParseResult.Rejected("Direct media files are not supported. Paste a YouTube URL instead.")
        }

        // Vimeo block
        if (urlParts.host.contains("vimeo")) {
            return ParseResult.Rejected("Vimeo support coming soon! For now, paste YouTube URLs.")
        }

        // Domain allowlist
        val hostClean = urlParts.host.lowercase().removePrefix("www.").removePrefix("m.")
        if (hostClean != "youtube.com" && hostClean != "youtu.be") {
            return ParseResult.Rejected("Only YouTube URLs are supported")
        }

        // Case-insensitive URL but preserve IDs
        val path = urlParts.path
        val query = urlParts.query

        // 1. Show URL: /show/VLPL...
        SHOW_PATH.find(path)?.let { match ->
            val plId = match.groupValues[1]
            return ParseResult.Success(ContentSource(
                type = SourceType.YT_PLAYLIST,
                id = plId,
                canonicalUrl = "https://www.youtube.com/playlist?list=$plId",
            ))
        }

        // 2. ?list=PL... (playlist param in any YouTube URL)
        LIST_PARAM.find(query)?.let { match ->
            val listId = match.groupValues[1]
            // Check if it's a valid playlist (PL prefix)
            if (listId.startsWith("PL")) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_PLAYLIST,
                    id = listId,
                    canonicalUrl = "https://www.youtube.com/playlist?list=$listId",
                ))
            }
            // Reject non-PL list params
            for (prefix in REJECT_LIST_PREFIXES) {
                if (listId.startsWith(prefix) || listId == prefix) {
                    return ParseResult.Rejected("Auto-generated playlists (mixes, uploads) are not supported. Paste a regular playlist URL.")
                }
            }
            // Non-PL list param — fall through to check for video
        }

        // 3. youtu.be/VIDEO_ID
        if (hostClean == "youtu.be") {
            YOUTU_BE_PATH.find(path)?.let { match ->
                val vid = match.groupValues[1]
                if (vid.isNotEmpty()) {
                    return ParseResult.Success(ContentSource(
                        type = SourceType.YT_VIDEO,
                        id = vid,
                        canonicalUrl = "https://www.youtube.com/watch?v=$vid",
                    ))
                }
            }
            return ParseResult.Rejected("Could not find a video ID in this URL")
        }

        // 4. /shorts/ID, /embed/ID, /v/ID, /live/ID
        PATH_VIDEO.find(path)?.let { match ->
            val vid = match.groupValues[1]
            if (vid.isNotEmpty()) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_VIDEO,
                    id = vid,
                    canonicalUrl = "https://www.youtube.com/watch?v=$vid",
                ))
            }
        }

        // 5. ?v=VIDEO_ID
        VIDEO_ID_PARAM.find(query)?.let { match ->
            val vid = match.groupValues[1]
            if (vid.isNotEmpty()) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_VIDEO,
                    id = vid,
                    canonicalUrl = "https://www.youtube.com/watch?v=$vid",
                ))
            }
        }

        // 6. /channel/UCxxxxx
        CHANNEL_UC.find(path)?.let { match ->
            val channelId = match.groupValues[1]
            if (channelId.length > 2) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_CHANNEL,
                    id = channelId,
                    canonicalUrl = "https://www.youtube.com/channel/$channelId",
                ))
            }
        }

        // 7. /@handle
        HANDLE.find(path)?.let { match ->
            val handle = match.groupValues[1]
            if (handle.isNotEmpty()) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_CHANNEL,
                    id = "@$handle",
                    canonicalUrl = "https://www.youtube.com/@$handle",
                ))
            }
        }

        // 8. /c/name or /user/name
        CUSTOM_CHANNEL.find(path)?.let { match ->
            val prefix = match.groupValues[1]
            val name = match.groupValues[2]
            if (name.isNotEmpty()) {
                return ParseResult.Success(ContentSource(
                    type = SourceType.YT_CHANNEL,
                    id = "$prefix/$name",
                    canonicalUrl = "https://www.youtube.com/$prefix/$name",
                ))
            }
        }

        // YouTube URL but no recognizable content
        return ParseResult.Rejected("Could not find a video, playlist, or channel in this YouTube URL")
    }

    private data class UrlParts(val host: String, val path: String, val query: String)

    private fun parseUrl(url: String): UrlParts? {
        return try {
            // Handle case-insensitive scheme
            val lower = url.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
            val withoutScheme = url.substringAfter("://")
            val hostAndRest = withoutScheme.split("/", limit = 2)
            val host = hostAndRest[0].lowercase()
            val pathAndQuery = if (hostAndRest.size > 1) "/" + hostAndRest[1] else "/"
            val parts = pathAndQuery.split("?", limit = 2)
            val path = parts[0].trimEnd('/')
            val query = if (parts.size > 1) "?" + parts[1] else ""
            UrlParts(host, path, query)
        } catch (_: Exception) {
            null
        }
    }
}
