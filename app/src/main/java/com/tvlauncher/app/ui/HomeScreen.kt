package com.tvlauncher.app.ui

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import com.tvlauncher.app.util.Launcher
import kotlin.math.abs

/** Acciones que dependen de Context (las provee la Activity; la UI no toca el framework). */
@Immutable
class LauncherActions(
    val launch: (AppEntry) -> Unit,
    val appInfo: (AppEntry) -> Unit,
    val systemSettings: () -> Unit,
    val networkSettings: () -> Unit,
    val setDefaultLauncher: () -> Unit,
    val accessibilitySettings: () -> Unit,
)

private const val COLUMNS = 5

// Curva de movimiento tipo Google TV (arranque reactivo, frenado fluido de inercia)
private val LeanbackScrollEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)

/**
 * BringIntoViewSpec estilo Google Play / Google TV Leanback:
 * - Pivot del 26% (alinea la fila activa con suficiente margen superior sin empujar la pantalla innecesariamente).
 * - Zona muerta de 1px, nada más: la animación recalcula la distancia en cada frame, y con una
 *   zona mayor frenaba antes de llegar; el tramo que faltaba lo completaba la siguiente tecla
 *   lateral como un ajuste visible.
 * - Interpolación de 110ms con curva de frenado. Un resorte de ~200 ms dio 7-10% de frames con
 *   tirones en el Mi Box 4 con pulsaciones seguidas, contra ~1% de este tween.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvGridBringIntoViewSpec = object : BringIntoViewSpec {
    private val parentFraction = 0.26f
    private val deadZonePx = 1f

    @Suppress("DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 110,
        easing = LeanbackScrollEasing,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset
        val trailingEdge = offset + size
        val itemSize = abs(trailingEdge - leadingEdge)
        val childSmallerThanParent = itemSize <= containerSize

        val initialTarget = parentFraction * containerSize
        val spaceAvailable = containerSize - initialTarget

        val targetForLeadingEdge = if (childSmallerThanParent && spaceAvailable < itemSize) {
            containerSize - itemSize
        } else {
            initialTarget
        }

        val scrollDelta = leadingEdge - targetForLeadingEdge
        return if (abs(scrollDelta) < deadZonePx) 0f else scrollDelta
    }
}

@StringRes
internal fun SortMode.labelRes(): Int = when (this) {
    SortMode.NAME -> R.string.sort_name
    SortMode.MOST_USED -> R.string.sort_most_used
    SortMode.RECENT -> R.string.sort_recent
}

@Composable
fun HomeRoot(vm: HomeViewModel, actions: LauncherActions) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val homeSignal by vm.homeSignal.collectAsStateWithLifecycle()
    val focused = remember { androidx.compose.runtime.mutableStateOf<AppEntry?>(null) }

    // Un launcher no se "cierra": Atrás vuelve al inicio o sale del modo mover.
    BackHandler(enabled = true) { vm.onHomePressed() }

    Box(Modifier.fillMaxSize()) {
        val ambient = rememberAmbient(focused)
        AmbientBackground(ambient)
        HomeContent(
            state = state,
            homeSignal = homeSignal,
            focused = focused,
            ambient = ambient,
            onCycleSort = vm::cycleSort,
            onOpenSettings = vm::openSettings,
            onLaunch = actions.launch,
            onMenu = vm::openMenu,
            onMovePinned = vm::movePinned,
            onStopMoving = vm::stopMoving,
        )
    }

    when (val o = overlay) {
        Overlay.None -> Unit
        Overlay.Settings -> {
            val isDefault = remember { Launcher.isDefaultLauncher(context) }
            SettingsDialog(
                sortMode = state.sortMode,
                hiddenApps = state.hiddenApps,
                isDefaultLauncher = isDefault,
                onDismiss = vm::dismissOverlay,
                onSort = vm::setSort,
                onUnhide = vm::unhide,
                onClearUsage = vm::clearUsage,
                onSetDefaultLauncher = actions.setDefaultLauncher,
                onOpenKeyMapping = vm::openKeyMapping,
                onSystem = actions.systemSettings,
                onNetwork = actions.networkSettings,
            )
        }
        Overlay.KeyMapping -> {
            KeyMappingDialog(
                allApps = state.apps,
                currentMappings = state.keyMappings,
                onDismiss = vm::dismissOverlay,
                onSaveMapping = vm::setKeyMapping,
                onRemoveMapping = vm::removeKeyMapping,
                onOpenAccessibilitySettings = actions.accessibilitySettings,
            )
        }
        is Overlay.Menu -> {
            val app = o.app
            val isPinned = state.pinned.any { it.key == app.key }
            AppMenuDialog(
                app = app,
                isPinned = isPinned,
                onDismiss = vm::dismissOverlay,
                onOpen = { vm.dismissOverlay(); actions.launch(app) },
                onTogglePin = { vm.togglePin(app); vm.dismissOverlay() },
                onStartMove = { vm.startMoving(app) },
                onHide = { vm.hide(app); vm.dismissOverlay() },
                onInfo = { actions.appInfo(app) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    homeSignal: Int,
    focused: androidx.compose.runtime.MutableState<AppEntry?>,
    ambient: Ambient,
    onCycleSort: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onMovePinned: (AppEntry, Int) -> Unit,
    onStopMoving: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val headerFocus = remember { FocusRequester() }
    val hasContent = !state.loading && state.apps.isNotEmpty()

    // FocusRequesters para dar la vuelta en la misma fila (wrap horizontal)
    val pinnedRequesters = remember(state.pinned.size) {
        List(state.pinned.size) { FocusRequester() }
    }
    val allAppsRequesters = remember(state.apps.size) {
        List(state.apps.size) { FocusRequester() }
    }

    // Al entrar y cada vez que se pulsa Home: arriba del todo y foco en la primera tarjeta.
    LaunchedEffect(homeSignal, hasContent) {
        if (hasContent) {
            gridState.scrollToItem(0)
            androidx.compose.runtime.withFrameNanos { }
            // La primera tarjeta ya tiene su propio FocusRequester (el de la fila), así que se pide
            // ese: firstFocus nunca llegaba a adjuntarse y el foco caía en el botón "Orden".
            val first = pinnedRequesters.firstOrNull() ?: allAppsRequesters.firstOrNull()
            runCatching { first?.requestFocus() }
        }
    }

    // Márgenes seguros de overscan de TV (~5%).
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
        Header(state.sortMode, onCycleSort, onOpenSettings, headerFocus)
        FocusedInfo(focused, state.usage, Modifier.fillMaxHeight(0.15f))
        when {
            state.loading -> CenterMessage(stringResource(R.string.loading))
            state.apps.isEmpty() -> CenterMessage(stringResource(R.string.empty))
            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides TvGridBringIntoViewSpec) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(COLUMNS),
                    modifier = Modifier.fillMaxWidth().weight(1f).fadingEdges(ambient, gridState, 24.dp),
                    // Margen para que la tarjeta enfocada (escala 1.10) no choque con el borde.
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            val isMoving = state.movingAppKey == app.key
                            val isFirstRow = index < COLUMNS

                            // Wrap horizontal en la misma fila
                            val rowStart = (index / COLUMNS) * COLUMNS
                            val rowEnd = minOf(rowStart + COLUMNS - 1, state.pinned.size - 1)
                            val isRowStart = index == rowStart
                            val isRowEnd = index == rowEnd

                            val cardModifier = Modifier.focusProperties {
                                if (isFirstRow) {
                                    up = headerFocus
                                }
                                if (isRowEnd && rowStart != rowEnd) {
                                    right = pinnedRequesters[rowStart]
                                }
                                if (isRowStart && rowStart != rowEnd) {
                                    left = pinnedRequesters[rowEnd]
                                }
                            }

                            AppCard(
                                app = app,
                                onClick = onLaunch,
                                onLongClick = onMenu,
                                modifier = cardModifier,
                                focusRequester = if (index < pinnedRequesters.size) pinnedRequesters[index] else null,
                                isMoving = isMoving,
                                onMoveLeft = { onMovePinned(app, -1) },
                                onMoveRight = { onMovePinned(app, 1) },
                                onStopMoving = onStopMoving,
                                onFocused = { focused.value = it },
                                badge = if (state.sortMode == SortMode.MOST_USED) {
                                    state.usage[app.key]?.count?.takeIf { it > 0 }?.let { "$it×" }
                                } else null,
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
                        val isFirstRow = !pinnedFirst && (index < COLUMNS)

                        // Wrap horizontal en la misma fila
                        val rowStart = (index / COLUMNS) * COLUMNS
                        val rowEnd = minOf(rowStart + COLUMNS - 1, state.apps.size - 1)
                        val isRowStart = index == rowStart
                        val isRowEnd = index == rowEnd

                        val cardModifier = Modifier.focusProperties {
                            if (isFirstRow) {
                                up = headerFocus
                            }
                            if (isRowEnd && rowStart != rowEnd) {
                                right = allAppsRequesters[rowStart]
                            }
                            if (isRowStart && rowStart != rowEnd) {
                                left = allAppsRequesters[rowEnd]
                            }
                        }

                        AppCard(
                            app = app,
                            onClick = onLaunch,
                            onLongClick = onMenu,
                            modifier = cardModifier,
                            focusRequester = if (index < allAppsRequesters.size) allAppsRequesters[index] else null,
                            onFocused = { focused.value = it },
                            badge = if (state.sortMode == SortMode.MOST_USED) {
                                state.usage[app.key]?.count?.takeIf { it > 0 }?.let { "$it×" }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    sortMode: SortMode,
    onCycleSort: () -> Unit,
    onOpenSettings: () -> Unit,
    headerFocus: FocusRequester,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Clock()
        Spacer(Modifier.weight(1f))
        PillButton(
            text = stringResource(R.string.sort_label, stringResource(sortMode.labelRes())),
            onClick = onCycleSort,
            focusRequester = headerFocus,
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
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val dateFormat = remember {
        java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMM"), java.util.Locale.getDefault())
    }
    val now by produceState(initialValue = System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                kotlinx.coroutines.delay(60_000L - (value % 60_000L) + 50L)
            }
        }
    }
    Column {
        Text(
            text = timeFormat.format(java.util.Date(now)),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            text = dateFormat.format(java.util.Date(now)).replaceFirstChar { it.uppercase() },
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
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x24FFFFFF),
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
