package com.junkfood.seal.download

import com.junkfood.seal.util.DownloadUtil

/** Builds a non-interactive MP3 task without changing the user's normal download preferences. */
object QuickDownload {
    fun audioMp3(url: String): Task {
        val preferences = DownloadUtil.DownloadPreferences.createFromPreferences().copy(
            extractAudio = true,
            convertAudio = true,
            audioConvertFormat = 0, // CONVERT_MP3
            formatIdString = "",
            mergeAudioStream = false,
        )
        return Task(url = url, preferences = preferences)
    }

    fun audioM4a(url: String): Task = Task(
        url = url,
        preferences = DownloadUtil.DownloadPreferences.createFromPreferences().copy(
            extractAudio = true, convertAudio = true, audioConvertFormat = 1, formatIdString = "",
        ),
    )

    fun video(url: String, maxHeight: Int): Task = Task(
        url = url,
        preferences = DownloadUtil.DownloadPreferences.createFromPreferences().copy(
            extractAudio = false, videoResolution = maxHeight, formatIdString = "",
        ),
    )
}
