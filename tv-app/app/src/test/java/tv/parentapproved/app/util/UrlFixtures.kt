package tv.parentapproved.app.util

/**
 * Test fixtures for ContentSourceParser — 73 URLs covering all supported and rejected patterns.
 */
data class TestUrl(
    val input: String,
    val expectedType: String?, // "yt_playlist", "yt_video", "yt_channel", or null for rejected
    val expectedId: String?,   // The extracted source ID, or null
    val description: String,
    val expectedMessage: String? = null, // Expected rejection message substring
)

val PLAYLIST_URLS = listOf(
    // 1-16: Playlist URLs
    TestUrl("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "yt_playlist", "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "standard playlist URL"),
    TestUrl("https://m.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "yt_playlist", "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "mobile playlist URL"),
    TestUrl("https://youtube.com/playlist?list=PLtest123abc", "yt_playlist", "PLtest123abc", "no-www playlist URL"),
    TestUrl("https://www.youtube.com/playlist?list=PLtest123&index=5", "yt_playlist", "PLtest123", "playlist URL with extra params"),
    TestUrl("https://www.youtube.com/watch?v=abc123&list=PLxyz789", "yt_playlist", "PLxyz789", "video URL with playlist param"),
    TestUrl("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "yt_playlist", "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "bare playlist ID"),
    TestUrl("  PLtest123  ", "yt_playlist", "PLtest123", "bare playlist ID with whitespace"),
    TestUrl("https://www.youtube.com/playlist?list=PLBsP89cpM05w-e2MXzp_UOO_0U36rmT42", "yt_playlist", "PLBsP89cpM05w-e2MXzp_UOO_0U36rmT42", "real PBS kids playlist"),
    TestUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf&index=3", "yt_playlist", "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", "video with playlist and index"),
    TestUrl("http://www.youtube.com/playlist?list=PLtest_http", "yt_playlist", "PLtest_http", "http (non-https) playlist URL"),
    TestUrl("https://www.youtube.com/show/VLPL123abc456", "yt_playlist", "PL123abc456", "show URL with VLPL prefix"),
    TestUrl("https://youtube.com/show/VLPLtest_show", "yt_playlist", "PLtest_show", "show URL no-www"),
    TestUrl("https://www.youtube.com/playlist?list=PL_underscore-dash", "yt_playlist", "PL_underscore-dash", "playlist ID with underscore and dash"),
    TestUrl("https://www.youtube.com/embed/vid1?list=PLembed_test", "yt_playlist", "PLembed_test", "embed URL with playlist param"),
    TestUrl("HTTPS://WWW.YOUTUBE.COM/PLAYLIST?LIST=PLuppercase", "yt_playlist", "PLuppercase", "uppercase URL"),
    TestUrl("https://www.youtube.com/playlist?feature=share&list=PLshare_test", "yt_playlist", "PLshare_test", "list param not first"),
)

val VIDEO_URLS = listOf(
    // 17-28: Video URLs
    TestUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "yt_video", "dQw4w9WgXcQ", "standard watch URL"),
    TestUrl("https://youtu.be/dQw4w9WgXcQ", "yt_video", "dQw4w9WgXcQ", "short URL"),
    TestUrl("https://youtu.be/dQw4w9WgXcQ?t=30", "yt_video", "dQw4w9WgXcQ", "short URL with timestamp"),
    TestUrl("https://m.youtube.com/watch?v=abc_123-XY", "yt_video", "abc_123-XY", "mobile watch URL"),
    TestUrl("https://www.youtube.com/shorts/shortVid123", "yt_video", "shortVid123", "shorts URL"),
    TestUrl("https://www.youtube.com/embed/embedVid1", "yt_video", "embedVid1", "embed URL"),
    TestUrl("https://www.youtube.com/v/oldStyleVid", "yt_video", "oldStyleVid", "old /v/ URL"),
    TestUrl("https://www.youtube.com/live/liveVid123", "yt_video", "liveVid123", "live URL"),
    TestUrl("https://youtube.com/watch?v=noWww123", "yt_video", "noWww123", "no-www watch URL"),
    TestUrl("https://www.youtube.com/watch?v=vid1&feature=share", "yt_video", "vid1", "watch URL with extra params"),
    TestUrl("https://www.youtube.com/watch?feature=share&v=vid2", "yt_video", "vid2", "watch URL v param not first"),
    TestUrl("http://youtu.be/httpVid1", "yt_video", "httpVid1", "http short URL"),
)

val CHANNEL_URLS = listOf(
    // 29-38: Channel URLs
    TestUrl("https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxxx", "yt_channel", "UCxxxxxxxxxxxxxxxxxxxxxxx", "channel URL with UC ID"),
    TestUrl("https://www.youtube.com/@PBSKids", "yt_channel", "@PBSKids", "handle URL"),
    TestUrl("https://youtube.com/@SesameStreet", "yt_channel", "@SesameStreet", "handle URL no-www"),
    TestUrl("https://m.youtube.com/@NatGeoKids", "yt_channel", "@NatGeoKids", "mobile handle URL"),
    TestUrl("https://www.youtube.com/c/PBSKids", "yt_channel", "c/PBSKids", "custom /c/ URL"),
    TestUrl("https://www.youtube.com/user/pbskids", "yt_channel", "user/pbskids", "legacy /user/ URL"),
    TestUrl("https://www.youtube.com/@Handle_with-special.chars", "yt_channel", "@Handle_with-special.chars", "handle with special chars"),
    TestUrl("https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxxx/videos", "yt_channel", "UCxxxxxxxxxxxxxxxxxxxxxxx", "channel URL with /videos suffix"),
    TestUrl("https://www.youtube.com/@PBSKids/videos", "yt_channel", "@PBSKids", "handle with /videos suffix"),
    TestUrl("https://www.youtube.com/@PBSKids/playlists", "yt_channel", "@PBSKids", "handle with /playlists suffix"),
)

val VIMEO_URLS = listOf(
    // 39-41: Vimeo URLs (rejected with message)
    TestUrl("https://vimeo.com/12345", null, null, "vimeo video URL", "Vimeo"),
    TestUrl("https://player.vimeo.com/video/12345", null, null, "vimeo embed URL", "Vimeo"),
    TestUrl("https://vimeo.com/channels/staffpicks/12345", null, null, "vimeo channel URL", "Vimeo"),
)

val DIRECT_FILE_URLS = listOf(
    // 42-44: Direct file URLs (rejected)
    TestUrl("https://example.com/video.mp4", null, null, "direct mp4 URL"),
    TestUrl("https://example.com/video.webm", null, null, "direct webm URL"),
    TestUrl("https://example.com/audio.mp3", null, null, "direct mp3 URL"),
)

val PRIVATE_IP_URLS = listOf(
    // 45-51: Private/local IPs (rejected)
    TestUrl("http://192.168.1.1/video", null, null, "private 192.168.x.x"),
    TestUrl("http://10.0.0.1/video", null, null, "private 10.x.x.x"),
    TestUrl("http://172.16.0.1/video", null, null, "private 172.16.x.x"),
    TestUrl("http://127.0.0.1/video", null, null, "localhost"),
    TestUrl("http://localhost/video", null, null, "localhost hostname"),
    TestUrl("http://0.0.0.0/video", null, null, "zero IP"),
    TestUrl("http://[::1]/video", null, null, "IPv6 localhost"),
)

val NON_VIDEO_URLS = listOf(
    // 52-59: Non-video URLs (rejected)
    TestUrl("https://www.google.com", null, null, "google"),
    TestUrl("https://twitter.com/user/status/123", null, null, "twitter"),
    TestUrl("not a url at all", null, null, "gibberish text"),
    TestUrl("", null, null, "empty string"),
    TestUrl("   ", null, null, "whitespace only"),
    TestUrl("https://www.youtube.com", null, null, "youtube home page"),
    TestUrl("https://www.youtube.com/results?search_query=test", null, null, "youtube search"),
    TestUrl("https://www.youtube.com/feed/trending", null, null, "youtube trending"),
)

val SHOW_EDGE_CASES = listOf(
    // 60-61: Show URL edge cases
    TestUrl("https://www.youtube.com/show/VLnotAPlaylist", null, null, "show URL without PL after VL"),
    TestUrl("https://www.youtube.com/show/", null, null, "show URL with empty path"),
)

val TRICKY_EDGE_CASES = listOf(
    // 62-73: Tricky edge cases
    TestUrl("https://www.youtube.com/watch?v=a&list=notPL", null, null, "list param without PL prefix → video", "yt_video_fallback"),
    TestUrl("youtube.com/watch?v=noScheme", "yt_video", "noScheme", "no scheme watch URL"),
    TestUrl("www.youtube.com/watch?v=wwwNoScheme", "yt_video", "wwwNoScheme", "www no scheme watch URL"),
    TestUrl("https://www.youtube.com/watch?v=", null, null, "empty video ID"),
    TestUrl("https://youtu.be/", null, null, "empty short URL"),
    TestUrl("https://www.youtube.com/channel/", null, null, "empty channel ID"),
    TestUrl("https://www.youtube.com/@", null, null, "empty handle"),
    TestUrl("https://www.youtube.com/playlist?list=", null, null, "empty playlist ID"),
    TestUrl("https://www.youtube.com/playlist?list=RD_randomMix", null, null, "mix/radio playlist (RD prefix)"),
    TestUrl("https://www.youtube.com/playlist?list=UU_uploadsPlaylist", null, null, "uploads playlist (UU prefix)"),
    TestUrl("https://www.youtube.com/playlist?list=LL", null, null, "liked videos playlist (LL)"),
    TestUrl("https://www.youtube.com/playlist?list=WL", null, null, "watch later playlist (WL)"),
)

val ALL_FIXTURES = PLAYLIST_URLS + VIDEO_URLS + CHANNEL_URLS + VIMEO_URLS +
    DIRECT_FILE_URLS + PRIVATE_IP_URLS + NON_VIDEO_URLS + SHOW_EDGE_CASES + TRICKY_EDGE_CASES
