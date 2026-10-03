package com.junkfood.seal.download

/** Turns common extractor failures into short, safe instructions for people using the app. */
object DownloadErrorMapper {
    fun message(error: Throwable): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("members-only", ignoreCase = true) ||
                detail.contains("Join this channel", ignoreCase = true) ->
                "This is members-only content. Join the channel and use an account that already has access."
            detail.contains("sign in", ignoreCase = true) ||
                detail.contains("login", ignoreCase = true) ||
                detail.contains("cookies", ignoreCase = true) ->
                "This source needs you to sign in. Use only your own authorized account or cookies."
            detail.contains("JavaScript runtime", ignoreCase = true) ->
                "This source needs a JavaScript runtime. Update yt-dlp, then try again."
            detail.isNotBlank() -> detail.lineSequence().first().take(240)
            else -> "The download could not be completed. Check the link and try again."
        }
    }
}
