package com.tvlauncher.app

import android.content.Intent
import android.os.Bundle
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

        val actions = LauncherActions(
            launch = { app ->
                if (Launcher.launchApp(this, app)) viewModel.recordLaunch(app) else toastCannotOpen(app)
            },
            appInfo = { app -> if (!Launcher.openAppInfo(this, app)) toastCannotOpen(app) },
            systemSettings = { Launcher.openSystemSettings(this) },
            networkSettings = { Launcher.openNetworkSettings(this) },
        )

        setContent {
            TvLauncherTheme {
                CompositionLocalProvider(LocalIconLoader provides container.iconLoader) {
                    HomeRoot(viewModel, actions)
                }
            }
        }
    }

    /** Botón Home estando ya en el launcher (o llegando desde otra app): volver al inicio. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) viewModel.onHomePressed()
    }

    private fun toastCannotOpen(app: AppEntry) {
        Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show()
    }
}
