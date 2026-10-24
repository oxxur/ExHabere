package org.exhabere.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.exhabere.ExHabereApplication
import org.exhabere.R
import org.exhabere.data.db.model.PauseOverlayPosition
import org.exhabere.data.db.model.TriggerOutcomeType
import org.exhabere.ui.overlay.LockoutOverlay
import org.exhabere.ui.theme.ExHabereTheme

/** User-initiated foreground service that hosts the local lockout countdown above other apps. */
class LockoutOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private var overlayView: ComposeView? = null
    private var method: String = ""
    private var overlayPosition: PauseOverlayPosition = PauseOverlayPosition.MIDDLE
    private var endAtMillis by mutableLongStateOf(0L)
    private var isRecordingOutcome: Boolean = false

    override fun onCreate() {
        super.onCreate()
        method = getString(R.string.mindful_pause)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_METHOD)
                .remove(KEY_END_AT_MILLIS)
                .apply()
            stopSelf()
            return START_NOT_STICKY
        }

        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (intent?.action == ACTION_END_EARLY) {
            endPauseEarly()
            return START_STICKY
        }

        if (intent?.action == ACTION_START) {
            method = intent.getStringExtra(EXTRA_METHOD)?.trim()?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.mindful_pause)
            endAtMillis = intent.getLongExtra(EXTRA_END_AT_MILLIS, 0L)
            if (endAtMillis <= 0L) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            preferences.edit()
                .putString(KEY_METHOD, method)
                .putLong(KEY_END_AT_MILLIS, endAtMillis)
                .apply()
        } else {
            method = preferences.getString(KEY_METHOD, getString(R.string.mindful_pause)) ?: getString(R.string.mindful_pause)
            endAtMillis = preferences.getLong(KEY_END_AT_MILLIS, 0L)
            if (endAtMillis <= 0L) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (!hasOverlayPermission(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        lifecycleOwner.onStart()
        serviceScope.launch {
            overlayPosition = (application as ExHabereApplication).container.habitRepository
                .getCurrentConfig()?.pauseOverlayPosition ?: PauseOverlayPosition.MIDDLE
            attachOverlay()
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun attachOverlay() {
        removeOverlay()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ExHabereTheme {
                    LockoutOverlay(
                        method = localizedMethodLabel(method),
                        endAtMillis = endAtMillis,
                        onOutcome = ::recordOutcome,
                        onEndEarly = ::endPauseEarly,
                        onTimerComplete = ::updateCompletionNotification,
                        fullScreen = overlayPosition == PauseOverlayPosition.FULL_SCREEN
                    )
                }
            }
        }
        val density = resources.displayMetrics.density
        val width = (340 * density).toInt().coerceAtMost(resources.displayMetrics.widthPixels - (32 * density).toInt())
        val isFullScreen = overlayPosition == PauseOverlayPosition.FULL_SCREEN
        val params = LayoutParams(
            if (isFullScreen) LayoutParams.MATCH_PARENT else width.coerceAtLeast((240 * density).toInt()),
            if (isFullScreen) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                LayoutParams.TYPE_PHONE
            },
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            when (overlayPosition) {
                PauseOverlayPosition.TOP -> {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (48 * density).toInt()
                }
                PauseOverlayPosition.MIDDLE -> gravity = Gravity.CENTER
                PauseOverlayPosition.BOTTOM -> {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (48 * density).toInt()
                }
                PauseOverlayPosition.FULL_SCREEN -> gravity = Gravity.TOP or Gravity.LEFT
            }
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { overlayView = view }
            .onFailure { stopSelf() }
    }

    private fun recordOutcome(outcomeType: TriggerOutcomeType) {
        if (isRecordingOutcome) return
        isRecordingOutcome = true
        serviceScope.launch {
            try {
                val logId = (application as ExHabereApplication).container.triggerLogRepository
                    .recordEmergencyOutcome(reliefMethod = method, outcomeType = outcomeType)
                getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                    .remove(KEY_METHOD)
                    .remove(KEY_END_AT_MILLIS)
                    .apply()
                removeOverlay()
                stopSelf()
                runCatching {
                    startActivity(
                        Intent(this@LockoutOverlayService, org.exhabere.MainActivity::class.java)
                            .putExtra(EXTRA_EMERGENCY_LOG_ID, logId)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                isRecordingOutcome = false
                Toast.makeText(this@LockoutOverlayService, getString(R.string.toast_outcome_save_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun endPauseEarly() {
        if (endAtMillis <= System.currentTimeMillis()) return

        endAtMillis = System.currentTimeMillis()
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_END_AT_MILLIS, endAtMillis)
            .apply()
        updateCompletionNotification()
    }

    private fun updateCompletionNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_emergency_pause), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.notification_channel_description) }
            )
        }
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_exhabere)
            .setContentTitle(
                if (endAtMillis > System.currentTimeMillis()) {
                    getString(R.string.notification_pause_active)
                } else {
                    getString(R.string.notification_pause_ended)
                }
            )
            .setContentText(localizedMethodLabel(method))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (endAtMillis > System.currentTimeMillis()) {
            val endEarlyIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_END_EARLY,
                Intent(this, LockoutOverlayService::class.java).setAction(ACTION_END_EARLY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_end_early),
                endEarlyIntent
            )
        }

        return builder.build()
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
            overlayView = null
        }
    }

    private fun localizedMethodLabel(value: String): String = when (value) {
        "Box Breathing Exercise" -> getString(R.string.relief_method_box_breathing)
        "Physiological Sigh" -> getString(R.string.relief_method_physiological_sigh)
        "Cold Water Reset" -> getString(R.string.relief_method_cold_water)
        "5-4-3-2-1 Sensory Grounding" -> getString(R.string.relief_method_grounding)
        "Take a mindful pause" -> getString(R.string.mindful_pause)
        else -> value
    }

    override fun onDestroy() {
        removeOverlay()
        if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            lifecycleOwner.onStop()
            lifecycleOwner.onDestroy()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "org.exhabere.action.START_LOCKOUT"
        const val ACTION_STOP = "org.exhabere.action.STOP_LOCKOUT"
        const val ACTION_END_EARLY = "org.exhabere.action.END_LOCKOUT_EARLY"
        const val EXTRA_METHOD = "org.exhabere.extra.LOCKOUT_METHOD"
        const val EXTRA_END_AT_MILLIS = "org.exhabere.extra.LOCKOUT_END_AT"
        const val EXTRA_EMERGENCY_LOG_ID = "org.exhabere.extra.EMERGENCY_LOG_ID"

        private const val CHANNEL_ID = "emergency_pause"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE_END_EARLY = 4202
        private const val PREFERENCES_NAME = "active_lockout_session"
        private const val KEY_METHOD = "method"
        private const val KEY_END_AT_MILLIS = "end_at_millis"
        fun start(context: Context, method: String, endAtMillis: Long) {
            val intent = Intent(context, LockoutOverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_METHOD, method)
                .putExtra(EXTRA_END_AT_MILLIS, endAtMillis)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun overlayPermissionIntent(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }
}
