package com.tvlauncher.app.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable

/** Una actividad lanzable. `key` (paquete/actividad) es la identidad estable en listas, usos y favoritos. */
@Immutable
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isLeanback: Boolean,
) {
    val key: String = "$packageName/$activityName"
    val component: ComponentName get() = ComponentName(packageName, activityName)
}
