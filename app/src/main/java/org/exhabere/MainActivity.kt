package org.exhabere

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.exhabere.service.LockoutOverlayService
import org.exhabere.ui.ExHabereApp
import org.exhabere.ui.theme.ExHabereTheme
import org.exhabere.viewmodel.EmergencyLaunchRequest

class MainActivity : ComponentActivity() {
    private var pendingOverlayRequest: EmergencyLaunchRequest? = null
    private var pendingNotificationRequest: EmergencyLaunchRequest? = null
    private var pendingEmergencyLogId by mutableStateOf<Long?>(null)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val request = pendingOverlayRequest
        pendingOverlayRequest = null
        if (request != null && LockoutOverlayService.hasOverlayPermission(this)) {
            startOverlay(request)
        } else if (request != null) {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingNotificationRequest
        pendingNotificationRequest = null
        if (request != null) {
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
            launchOverlayService(request)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingEmergencyLogId = intent?.getLongExtra(LockoutOverlayService.EXTRA_EMERGENCY_LOG_ID, -1L)
            ?.takeIf { it > 0L }
        enableEdgeToEdge()
        val app = application as ExHabereApplication
        setContent {
            ExHabereTheme {
                ExHabereApp(
                    container = app.container,
                    onStartOverlay = ::startOverlay,
                    onRequestOverlayPermission = { request ->
                        pendingOverlayRequest = request
                        overlayPermissionLauncher.launch(LockoutOverlayService.overlayPermissionIntent(this))
                    },
                    pendingEmergencyLogId = pendingEmergencyLogId,
                    onEmergencyLogDetailsHandled = { pendingEmergencyLogId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEmergencyLogId = intent.getLongExtra(LockoutOverlayService.EXTRA_EMERGENCY_LOG_ID, -1L)
            .takeIf { it > 0L }
    }

    private fun startOverlay(request: EmergencyLaunchRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationRequest = request
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.overlay_notification_permission_title))
                .setMessage(
                    getString(R.string.overlay_notification_permission_message)
                )
                .setPositiveButton(getString(R.string.continue_action)) { _, _ ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton(getString(R.string.not_now)) { _, _ ->
                    pendingNotificationRequest = null
                    launchOverlayService(request)
                }
                .setOnCancelListener {
                    pendingNotificationRequest = null
                    launchOverlayService(request)
                }
                .show()
            return
        }
        launchOverlayService(request)
    }

    private fun launchOverlayService(request: EmergencyLaunchRequest) {
        runCatching {
            LockoutOverlayService.start(this, request.method, request.endAtMillis)
        }.onSuccess {
            moveTaskToBack(true)
        }.onFailure {
            Toast.makeText(this, getString(R.string.timer_start_failed), Toast.LENGTH_LONG).show()
        }
    }
}
