package com.tvlauncher.app.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tvlauncher.app.R
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.data.SortMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Acciones que dependen de Context (las provee la Activity; la UI no toca el framework). */
@Immutable
class LauncherActions(
    val launch: (AppEntry) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val systemSettings: () -> Unit,
    val networkSettings: () -> Unit,
)

private const val COLUMNS = 5

@StringRes
internal fun SortMode.labelRes(): Int = when (this) {
    SortMode.NAME -> R.string.sort_name
    SortMode.MOST_USED -> R.string.sort_most_used
    SortMode.RECENT -> R.string.sort_recent
}

@Composable
fun HomeRoot(vm: HomeViewModel, actions: LauncherActions) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val homeSignal by vm.homeSignal.collectAsStateWithLifecycle()

    // Un launcher no se "cierra": Atrás vuelve al inicio. (Los diálogos manejan su propio Atrás.)
    BackHandler(enabled = true) { vm.onHomePressed() }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.Background),
    ) {
        HomeContent(
            state = state,
            homeSignal = homeSignal,
            onCycleSort = vm::cycleSort,
            onOpenSettings = vm::openSettings,
            onLaunch = actions.launch,
            onMenu = vm::openMenu,
        )
    }

    when (val o = overlay) {
        Overlay.None -> Unit
        Overlay.Settings -> SettingsDialog(
            sortMode = state.sortMode,
            hiddenApps = state.hiddenApps,
            onDismiss = vm::dismissOverlay,
            onSort = vm::setSort,
            onUnhide = vm::unhide,
            onClearUsage = vm::clearUsage,
            onSystem = actions.systemSettings,
            onNetwork = actions.networkSettings,
        )
        is Overlay.Menu -> {
            val app = o.app
            val pinnedIndex = state.pinned.indexOfFirst { it.key == app.key }
            AppMenuDialog(
                app = app,
                pinnedIndex = pinnedIndex,
                pinnedCount = state.pinned.size,
                onDismiss = vm::dismissOverlay,
                onOpen = { vm.dismissOverlay(); actions.launch(app) },
                onTogglePin = { vm.togglePin(app); vm.dismissOverlay() },
                onMove = { delta -> vm.movePinned(app, delta) },
                onHide = { vm.hide(app); vm.dismissOverlay() },
                onInfo = { actions.appInfo(app) },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    homeSignal: Int,
    onCycleSort: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val hasContent = !state.loading && state.apps.isNotEmpty()

    // Al entrar y cada vez que se pulsa Home: arriba del todo y foco en la primera tarjeta.
    LaunchedEffect(homeSignal, hasContent) {
        if (hasContent) {
            gridState.scrollToItem(0)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
    }

    // Márgenes seguros de overscan de TV (~5%).
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Header(state.sortMode, onCycleSort, onOpenSettings)
        when {
            state.loading -> CenterMessage(stringResource(R.string.loading))
            state.apps.isEmpty() -> CenterMessage(stringResource(R.string.empty))
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize(),
                // padding extra: la tarjeta enfocada crece (scale) y no debe recortarse en los bordes
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val pinnedFirst = state.pinned.isNotEmpty()
                if (pinnedFirst) {
                    item(key = "h_fav", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        SectionTitle(stringResource(R.string.favorites))
                    }
                    itemsIndexed(
                        items = state.pinned,
                        key = { _, app -> "p:" + app.key },
                        contentType = { _, _ -> "app" },
                    ) { index, app ->
                        AppCard(
                            app = app,
                            onClick = onLaunch,
                            onLongClick = onMenu,
                            focusRequester = if (index == 0) firstFocus else null,
                        )
                    }
                    item(key = "h_all", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        SectionTitle(stringResource(R.string.all_apps))
                    }
                }
                itemsIndexed(
                    items = state.apps,
                    key = { _, app -> "a:" + app.key },
                    contentType = { _, _ -> "app" },
                ) { index, app ->
                    AppCard(
                        app = app,
                        onClick = onLaunch,
                        onLongClick = onMenu,
                        focusRequester = if (index == 0 && !pinnedFirst) firstFocus else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(sortMode: SortMode, onCycleSort: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Clock()
        Spacer(Modifier.weight(1f))
        PillButton(
            text = stringResource(R.string.sort_label, stringResource(sortMode.labelRes())),
            onClick = onCycleSort,
        )
        Spacer(Modifier.width(12.dp))
        PillButton(text = stringResource(R.string.settings), onClick = onOpenSettings)
    }
}

/** Reloj aislado: solo él se recompone cada minuto (no toca la grilla) y se detiene si la app no está visible. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) } // respeta 12/24 h del sistema
    val dateFormat = remember {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault())
    }
    val now by produceState(initialValue = System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000L - (value % 60_000L) + 50L)
            }
        }
    }
    Column {
        Text(
            text = timeFormat.format(Date(now)),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            text = dateFormat.format(Date(now)).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = LauncherColors.TextDim,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LauncherColors.TextDim,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = LauncherColors.TextDim)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LauncherColors.Card,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
