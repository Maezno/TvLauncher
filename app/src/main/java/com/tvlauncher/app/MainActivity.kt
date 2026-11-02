package com.tvlauncher.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.ui.HomeRoot
import com.tvlauncher.app.ui.HomeViewModel
import com.tvlauncher.app.ui.LauncherActions
import com.tvlauncher.app.ui.LocalIconLoader
import com.tvlauncher.app.ui.TvLauncherTheme
import com.tvlauncher.app.util.Launcher

class MainActivity : ComponentActivity() {

    private val container get() = (application as TvLauncherApp).container

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel((application as TvLauncherApp).container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleAppsOrHomeIntent(intent)

        val actions = LauncherActions(
            launch = { app ->
                if (Launcher.launchApp(this, app)) viewModel.recordLaunch(app) else toastCannotOpen(app)
            },
            appInfo = { app -> if (!Launcher.openAppInfo(this, app)) toastCannotOpen(app) },
            systemSettings = { Launcher.openSystemSettings(this) },
            networkSettings = { Launcher.openNetworkSettings(this) },
            setDefaultLauncher = { Launcher.openDefaultLauncherSettings(this) },
            accessibilitySettings = { Launcher.openAccessibilitySettings(this) },
        )

        setContent {
            TvLauncherTheme {
                CompositionLocalProvider(LocalIconLoader provides container.iconLoader) {
                    HomeRoot(viewModel, actions)
                }
            }
        }
        // El fondo de Compose es opaco y cubre toda la ventana: quitar el windowBackground evita
        // pintar 1920x1080 de más en cada frame (overdraw). Se hace tras el primer frame para no
        // perder el fondo oscuro del arranque.
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    /** Botón Home o Apps estando ya en el launcher (o llegando desde otra app): volver al inicio. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAppsOrHomeIntent(intent)
    }

    private fun handleAppsOrHomeIntent(intent: Intent?) {
        if (intent == null) return
        val isHome = intent.hasCategory(Intent.CATEGORY_HOME)
        val isAllApps = intent.action == "android.intent.action.ALL_APPS" ||
                intent.action == Intent.ACTION_ALL_APPS
        if (isHome || isAllApps) {
            viewModel.onHomePressed()
        }
    }

    /**
     * Mapeo de botones de controles remotos (Xiaomi, Android TV genéricos).
     * Intercepta el botón de Apps (KEYCODE_ALL_APPS = 284) para ir a la grilla de aplicaciones.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ALL_APPS || keyCode == 284) {
            viewModel.onHomePressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun toastCannotOpen(app: AppEntry) {
        Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
    }
}
