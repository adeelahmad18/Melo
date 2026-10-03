package com.junkfood.seal.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A cancellable timer that pauses active tasks; it never force-closes the app. */
class SleepTimer(private val downloader: DownloaderV2, private val scope: CoroutineScope) {
    private var timer: Job? = null

    fun start(minutes: Int, onFinished: () -> Unit = {}) {
        require(minutes in 1..720)
        timer?.cancel()
        timer = scope.launch {
            delay(minutes * 60_000L)
            downloader.getTaskStateMap().keys.toList().forEach(downloader::cancel)
            onFinished()
        }
    }

    fun cancel() { timer?.cancel(); timer = null }
}
