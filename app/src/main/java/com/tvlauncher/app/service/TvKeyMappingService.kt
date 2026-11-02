package com.tvlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tvlauncher.app.MainActivity
import com.tvlauncher.app.TvLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad para interceptar y remapear botones físicos del control remoto
 * (botón de Apps Xiaomi KEYCODE_ALL_APPS=284 / scancode 580, botones de acceso directo, etc.).
 *
 * Solo recibe eventos de teclas: no lee contenido de pantalla ni eventos de accesibilidad,
 * no registra nada en logcat y no guarda las teclas pulsadas.
 */
class TvKeyMappingService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Copia en memoria de los mapeos: onKeyEvent corre en el hilo principal por cada tecla
    // del sistema, así que nunca debe tocar disco ni bloquear.
    @Volatile
    private var keyMappings: Map<Int, String> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as? TvLauncherApp ?: return
        scope.launch {
            app.container.store.snapshot
                .map { it.keyMappings }
                .distinctUntilChanged()
                .collect { keyMappings = it }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isAppsKey = keyCode == KeyEvent.KEYCODE_ALL_APPS || event.scanCode == XIAOMI_APPS_SCANCODE
        val action = keyMappings[keyCode]
        if (!isAppsKey && action == null) return false

        // Consumimos también el ACTION_UP para que la app en primer plano no reciba media pulsación.
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true

        val handled = when {
            isAppsKey || action == "HOME" || action == "APPS" -> launchHome()
            action != null && action.startsWith("APP:") -> launchPackage(action.removePrefix("APP:"))
            else -> false
        }
        return handled
    }

    private fun launchHome(): Boolean = start(
        Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
    )

    private fun launchPackage(packageName: String): Boolean {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false // app desinstalada: dejamos pasar la tecla
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val XIAOMI_APPS_SCANCODE = 580
    }
}
