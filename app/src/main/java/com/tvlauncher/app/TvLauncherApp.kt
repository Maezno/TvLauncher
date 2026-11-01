package com.tvlauncher.app

import android.app.Application
import com.tvlauncher.app.data.AppRepository
import com.tvlauncher.app.data.IconLoader
import com.tvlauncher.app.data.LauncherStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TvLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Grafo de dependencias manual: sin Hilt/Dagger (arranque más rápido, APK más chico). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = LauncherStore(app)
    val repository = AppRepository(app, appScope)
    val iconLoader = IconLoader(app.packageManager, appScope)

    init {
        repository.start() // todo el trabajo va a hilos de fondo; onCreate no se bloquea
        appScope.launch { repository.changedPackages.collect { iconLoader.evict(it) } }
    }
}
