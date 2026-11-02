package com.tvlauncher.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvlauncher.app.AppContainer
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.data.SortMode
import com.tvlauncher.app.data.StoreSnapshot
import com.tvlauncher.app.data.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val sortMode: SortMode = SortMode.NAME,
    val pinned: List<AppEntry> = emptyList(),
    val apps: List<AppEntry> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val usage: Map<String, Usage> = emptyMap(),
    val keyMappings: Map<Int, String> = emptyMap(),
    val movingAppKey: String? = null,
)

sealed interface Overlay {
    data object None : Overlay
    data object Settings : Overlay
    data object KeyMapping : Overlay
    data class Menu(val app: AppEntry) : Overlay
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.store

    private val _movingAppKey = MutableStateFlow<String?>(null)

    /** Ordenar/combinar ocurre en Dispatchers.Default; la UI recibe un estado inmutable ya listo. */
    val uiState: StateFlow<HomeUiState> = combine(
        container.repository.apps,
        container.repository.ready,
        store.snapshot,
        _movingAppKey,
    ) { apps, ready, snap, movingKey -> buildState(apps, ready, snap, movingKey) }
        .onEach { if (!it.loading) container.iconLoader.preload(it.pinned + it.apps) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    /** Se incrementa cada vez que el usuario vuelve a "Home": la UI sube al inicio y enfoca el primer item. */
    private val _homeSignal = MutableStateFlow(0)
    val homeSignal: StateFlow<Int> = _homeSignal.asStateFlow()

    fun openMenu(app: AppEntry) { _overlay.value = Overlay.Menu(app) }
    fun openSettings() { _overlay.value = Overlay.Settings }
    fun openKeyMapping() { _overlay.value = Overlay.KeyMapping }
    fun dismissOverlay() { _overlay.value = Overlay.None }

    fun setKeyMapping(keyCode: Int, action: String) {
        container.appScope.launch { store.setKeyMapping(keyCode, action) }
    }

    fun removeKeyMapping(keyCode: Int) {
        container.appScope.launch { store.removeKeyMapping(keyCode) }
    }

    fun startMoving(app: AppEntry) {
        _overlay.value = Overlay.None
        _movingAppKey.value = app.key
    }

    fun stopMoving() {
        _movingAppKey.value = null
    }

    fun onHomePressed() {
        if (_movingAppKey.value != null) {
            _movingAppKey.value = null
            return
        }
        _overlay.value = Overlay.None
        _homeSignal.update { it + 1 }
    }

    fun cycleSort() {
        val modes = SortMode.entries
        setSort(modes[(uiState.value.sortMode.ordinal + 1) % modes.size])
    }

    fun setSort(mode: SortMode) { container.appScope.launch { store.setSort(mode) } }
    fun recordLaunch(app: AppEntry) { container.appScope.launch { store.recordLaunch(app.key) } }
    fun togglePin(app: AppEntry) { container.appScope.launch { store.togglePinned(app.key) } }
    fun movePinned(app: AppEntry, delta: Int) { container.appScope.launch { store.movePinned(app.key, delta) } }
    fun hide(app: AppEntry) { container.appScope.launch { store.hide(app.key) } }
    fun unhide(app: AppEntry) { container.appScope.launch { store.unhide(app.key) } }
    fun clearUsage() { container.appScope.launch { store.clearUsage() } }
}

private val primaryCollator: Collator by lazy { Collator.getInstance().apply { strength = Collator.PRIMARY } }

private fun buildState(
    apps: List<AppEntry>,
    ready: Boolean,
    snap: StoreSnapshot,
    movingKey: String?,
): HomeUiState {
    if (!ready) return HomeUiState(loading = true, sortMode = snap.sortMode, movingAppKey = movingKey)

    val byName = Comparator<AppEntry> { a, b -> primaryCollator.compare(a.label, b.label) }

    val visible = apps.filter { it.key !in snap.hidden }
    val usage = snap.usage

    val sorted = when (snap.sortMode) {
        SortMode.NAME -> visible.sortedWith(byName)
        SortMode.MOST_USED -> visible.sortedWith(Comparator { a, b ->
            val ua = usage[a.key]
            val ub = usage[b.key]
            val byCount = (ub?.count ?: 0).compareTo(ua?.count ?: 0)
            if (byCount != 0) byCount else {
                val byTime = (ub?.lastLaunch ?: 0L).compareTo(ua?.lastLaunch ?: 0L)
                if (byTime != 0) byTime else byName.compare(a, b)
            }
        })
        SortMode.RECENT -> visible.sortedWith(Comparator { a, b ->
            val byTime = (usage[b.key]?.lastLaunch ?: 0L).compareTo(usage[a.key]?.lastLaunch ?: 0L)
            if (byTime != 0) byTime else byName.compare(a, b)
        })
    }

    val byKey = apps.associateBy { it.key }
    val pinned = snap.pinned.mapNotNull { byKey[it] }.filter { it.key !in snap.hidden }
    val hidden = snap.hidden.mapNotNull { byKey[it] }.sortedWith(byName)

    return HomeUiState(
        loading = false,
        sortMode = snap.sortMode,
        pinned = pinned,
        apps = sorted,
        hiddenApps = hidden,
        usage = usage,
        keyMappings = snap.keyMappings,
        movingAppKey = movingKey,
    )
}
