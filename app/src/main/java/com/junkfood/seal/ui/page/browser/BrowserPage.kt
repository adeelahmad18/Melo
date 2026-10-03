package com.junkfood.seal.ui.page.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView as AndroidWebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.QuickDownload
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

private data class QuickSite(val name: String, val url: String)

private val quickSites = listOf(
    QuickSite("YouTube", "https://m.youtube.com"),
    QuickSite("TikTok", "https://www.tiktok.com"),
    QuickSite("Snapchat", "https://www.snapchat.com"),
)

/** A user-controlled browser. It only offers the current page to Seal's normal download sheet. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPage(
    onNavigateBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    dialogViewModel: DownloadDialogViewModel,
) {
    val downloader: DownloaderV2 = remember { GlobalContext.get().get() }
    var currentUrl by remember { mutableStateOf(quickSites.first().url) }
    var addressText by remember { mutableStateOf("") }
    var downloadableUrl by remember { mutableStateOf<String?>(null) }
    var showQuickChoices by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf("Download started") }
    var downloadButtonOffset by remember { mutableStateOf(Offset.Zero) }
    val webViewState = rememberWebViewState(currentUrl)

    fun navigateToAddress(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) return
        val destination =
            when {
                input.startsWith("https://") || input.startsWith("http://") -> input
                input.contains('.') && !input.contains(' ') -> "https://$input"
                else -> "https://www.google.com/search?q=${Uri.encode(input)}"
            }
        addressText = destination
        currentUrl = destination
        webViewState.content = com.google.accompanist.web.WebContent.Url(destination)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(webViewState.pageTitle?.toString() ?: "Browser", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            // Keep the action discoverable. Sites such as YouTube change video pages without
            // a full WebView reload, so waiting only for a page-finished callback hides it.
            AnimatedVisibility(visible = true) {
                ExtendedFloatingActionButton(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(
                                        downloadButtonOffset.x.roundToInt(),
                                        downloadButtonOffset.y.roundToInt(),
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        downloadButtonOffset += dragAmount
                                    }
                                },
                        icon = { Icon(Icons.Outlined.Download, null) },
                        text = { Text("Download") },
                        onClick = {
                            if (isDownloadCandidate(currentUrl)) {
                                downloadableUrl = currentUrl
                                showQuickChoices = true
                            } else {
                                bannerMessage = "Open an individual video or post first"
                                showBanner = true
                            }
                        },
                    )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                label = { Text("Search or enter website") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                trailingIcon = {
                    IconButton(onClick = { navigateToAddress(addressText) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Open search or website")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateToAddress(addressText) }),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickSites) { site ->
                    FilterChip(
                        selected = currentUrl == site.url,
                        onClick = { navigateToAddress(site.url) },
                        label = { Text(site.name) },
                    )
                }
            }
            val client = remember {
                object : AccompanistWebViewClient() {
                    override fun shouldOverrideUrlLoading(view: AndroidWebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            currentUrl = url
                            addressText = url
                            downloadableUrl = url.takeIf(::isSupportedMediaPage)
                            return false
                        }
                        return true
                    }

                    override fun onPageFinished(view: AndroidWebView, url: String?) {
                        super.onPageFinished(view, url)
                        currentUrl = url.orEmpty()
                        addressText = url.orEmpty()
                        downloadableUrl = url?.takeIf(::isSupportedMediaPage)
                    }

                    override fun doUpdateVisitedHistory(
                        view: AndroidWebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        currentUrl = url.orEmpty()
                        addressText = url.orEmpty()
                        downloadableUrl = url?.takeIf(::isDownloadCandidate)
                    }
                }
            }
            WebView(
                state = webViewState,
                client = client,
                modifier = Modifier.weight(1f).fillMaxSize(),
                factory = { context -> AndroidWebView(context).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true } },
            )
        }
        }
        AnimatedVisibility(
            visible = showBanner,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DownloadForOffline, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface)
                    Text(bannerMessage, color = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    TextButton(onClick = { showBanner = false }) { Text("Not now") }
                    Button(onClick = { showBanner = false; onOpenDownloads() }) { Text("See downloads") }
                }
            }
        }
    }
    if (showQuickChoices) {
        ModalBottomSheet(onDismissRequest = { showQuickChoices = false }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(webViewState.pageTitle?.toString() ?: "Choose download", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Music")
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { downloadableUrl?.let { downloader.enqueue(QuickDownload.audioMp3(it)) }; showQuickChoices = false; bannerMessage = "Download started"; showBanner = true }) { Text("MP3") }
                    Button(onClick = { downloadableUrl?.let { downloader.enqueue(QuickDownload.audioM4a(it)) }; showQuickChoices = false; bannerMessage = "Download started"; showBanner = true }) { Text("M4A") }
                }
                Text("Video")
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { downloadableUrl?.let { downloader.enqueue(QuickDownload.video(it, 3)) }; showQuickChoices = false; bannerMessage = "Download started"; showBanner = true }) { Text("1080p") }
                    Button(onClick = { downloadableUrl?.let { downloader.enqueue(QuickDownload.video(it, 4)) }; showQuickChoices = false; bannerMessage = "Download started"; showBanner = true }) { Text("720p") }
                    Button(onClick = { downloadableUrl?.let { downloader.enqueue(QuickDownload.video(it, 7)) }; showQuickChoices = false; bannerMessage = "Download started"; showBanner = true }) { Text("Best") }
                }
            }
        }
    }
}

private fun isSupportedMediaPage(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = uri.host.orEmpty().lowercase()
    val path = uri.path.orEmpty()
    return when {
        host == "youtu.be" -> path.trim('/').isNotEmpty()
        host.endsWith("youtube.com") -> path.startsWith("/watch") || path.startsWith("/shorts/") || path.startsWith("/live/")
        host.endsWith("tiktok.com") -> path.startsWith("/@") || path.startsWith("/t/") || path.startsWith("/video/")
        host.endsWith("snapchat.com") -> path.startsWith("/spotlight/") || path.startsWith("/p/")
        else -> false
    }
}

/** Lets yt-dlp handle its supported sites without incorrectly offering root homepages as media. */
private fun isDownloadCandidate(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false
    return uri.path.orEmpty().trim('/').isNotEmpty() || !uri.rawQuery.isNullOrBlank()
}
