package com.tvlauncher.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tvlauncher.app.data.AppEntry

/** Lanzado de apps e intents del sistema. Siempre explícitos o de Ajustes; nunca se loguea nada. */
object Launcher {

    fun launchApp(context: Context, app: AppEntry): Boolean {
        val category = if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(context, intent)
    }

    /** Pantalla de info de la app del sistema (desde ahí se desinstala; evita pedir REQUEST_DELETE_PACKAGES). */
    fun openAppInfo(context: Context, app: AppEntry): Boolean = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun openSystemSettings(context: Context): Boolean = startFirst(context, Intent(Settings.ACTION_SETTINGS))

    fun openNetworkSettings(context: Context): Boolean = startFirst(
        context,
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    private fun startFirst(context: Context, vararg intents: Intent): Boolean =
        intents.any { start(context, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
