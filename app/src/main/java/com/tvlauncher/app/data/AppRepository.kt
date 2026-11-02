package com.tvlauncher.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lista de apps lanzables. Todo el trabajo pesado (PackageManager, etiquetas) ocurre en IO;
 * la UI solo observa un StateFlow inmutable. Sin permisos especiales: usa <queries> del manifest.
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val pm: PackageManager = context.packageManager

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _changedPackages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Paquetes instalados/actualizados/eliminados: sirve para invalidar iconos en caché. */
    val changedPackages: SharedFlow<String> = _changedPackages.asSharedFlow()

    private var debounceJob: Job? = null // solo se toca desde el hilo principal (receiver)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { _changedPackages.tryEmit(it) }
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(REFRESH_DEBOUNCE_MS.milliseconds) // una instalación dispara varios broadcasts
                load()
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { load() }
    }

    private suspend fun load() {
        val list = kotlinx.coroutines.withContext(Dispatchers.IO) { query() }
        _apps.value = list
        _ready.value = true
    }

    private suspend fun query(): List<AppEntry> = coroutineScope {
        val self = context.packageName
        val tvInfoList = resolve(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPackages = tvInfoList.mapTo(HashSet(tvInfoList.size * 2)) { it.activityInfo.packageName }

        // Apps pensadas para TV tienen prioridad; las de móvil solo aparecen si el paquete no tiene entrada TV.
        val mobileInfoList = resolve(Intent.CATEGORY_LAUNCHER).filter { it.activityInfo.packageName !in tvPackages }

        val tvDeferred = tvInfoList.map { info ->
            async(Dispatchers.IO) { toEntry(info, leanback = true, self) }
        }
        val mobileDeferred = mobileInfoList.map { info ->
            async(Dispatchers.IO) { toEntry(info, leanback = false, self) }
        }

        val result = ArrayList<AppEntry>(tvInfoList.size + mobileInfoList.size)
        tvDeferred.forEach { d -> d.await()?.let(result::add) }
        mobileDeferred.forEach { d -> d.await()?.let(result::add) }
        result.distinctBy { it.key }
    }

    private fun toEntry(info: ResolveInfo, leanback: Boolean, self: String): AppEntry? {
        val ai = info.activityInfo ?: return null
        if (ai.packageName == self) return null
        val label = info.loadLabel(pm).toString().trim().ifEmpty { ai.packageName }
        return AppEntry(ai.packageName, ai.name, label, leanback)
    }

    private fun resolve(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 500L
    }
}
