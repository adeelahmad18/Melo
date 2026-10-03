package com.junkfood.seal.clipboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.junkfood.seal.R
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.Task
import com.junkfood.seal.util.DownloadUtil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Opt-in foreground service for clipboard URL handoff. Android limits clipboard access while an app
 * is in the background, so its persistent notification is intentional and should not be hidden.
 */
class ClipboardHandoffService : Service(), KoinComponent {
    private val downloader: DownloaderV2 by inject()
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var badge: View? = null
    private var pendingUrl: String? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { inspectClipboard() }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(listener)
        removeBadge()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun inspectClipboard() {
        if (!clipboard.hasPrimaryClip() || clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) return
        val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val url = value.takeIf(::isSupportedUrl) ?: return
        pendingUrl = url
        if (Settings.canDrawOverlays(this)) showBadge() else Toast.makeText(this, "Link copied. Enable overlay to use quick download.", Toast.LENGTH_SHORT).show()
    }

    private fun showBadge() {
        removeBadge()
        val text = TextView(this).apply {
            text = "↓"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(31, 96, 145))
            setPadding(28, 18, 28, 18)
            setOnClickListener {
                pendingUrl?.let {
                    downloader.enqueue(Task(url = it, preferences = DownloadUtil.DownloadPreferences.createFromPreferences()))
                    Toast.makeText(this@ClipboardHandoffService, "Added to download queue", Toast.LENGTH_SHORT).show()
                }
                removeBadge()
            }
        }
        badge = text
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        windowManager.addView(text, params)
        text.postDelayed(::removeBadge, BADGE_DURATION_MS)
    }

    private fun removeBadge() { badge?.let(windowManager::removeViewImmediate); badge = null }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_seal).setContentTitle("Clipboard handoff is on")
        .setContentText("Watching copied links only while this notification is visible.").setOngoing(true).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NotificationManager::class.java)).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Clipboard handoff", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun isSupportedUrl(value: String): Boolean = runCatching {
        val host = java.net.URI(value).host.orEmpty().lowercase()
        value.startsWith("https://") && (host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith("tiktok.com") || host.endsWith("snapchat.com"))
    }.getOrDefault(false)

    companion object { private const val CHANNEL_ID = "clipboard_handoff"; private const val NOTIFICATION_ID = 1207; private const val BADGE_DURATION_MS = 3_000L }
}

/** Entry points for a settings toggle. Never request overlay permission without a user action. */
object ClipboardHandoff {
    fun requestOverlayPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun start(context: Context) {
        check(Settings.canDrawOverlays(context)) { "Overlay permission is required before enabling clipboard handoff." }
        ContextCompat.startForegroundService(context, Intent(context, ClipboardHandoffService::class.java))
    }

    fun stop(context: Context) = context.stopService(Intent(context, ClipboardHandoffService::class.java))
}
