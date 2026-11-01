package com.tvlauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tvlauncher.app.R
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.data.SortMode

/**
 * Los paneles usan Dialog (ventana propia): el foco del D-pad queda atrapado dentro y no se escapa
 * a la grilla de abajo. Solo se crean cuando se abren, así que no cuestan nada en el home.
 */
@Composable
private fun PanelDialog(onDismiss: () -> Unit, width: Int, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LauncherColors.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(width.dp)
                    .background(LauncherColors.Panel, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) { content() }
        }
    }
}

@Composable
private fun RequestFocusOnce(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
fun AppMenuDialog(
    app: AppEntry,
    pinnedIndex: Int,
    pinnedCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: (Int) -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
) {
    val first = remember { FocusRequester() }
    PanelDialog(onDismiss, width = 440) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            PanelRow(stringResource(R.string.menu_open), onOpen, Modifier.focusRequester(first))
            PanelRow(
                stringResource(if (pinnedIndex >= 0) R.string.menu_unpin else R.string.menu_pin),
                onTogglePin,
            )
            if (pinnedIndex > 0) PanelRow(stringResource(R.string.menu_move_left), { onMove(-1) })
            if (pinnedIndex >= 0 && pinnedIndex < pinnedCount - 1) {
                PanelRow(stringResource(R.string.menu_move_right), { onMove(1) })
            }
            PanelRow(stringResource(R.string.menu_hide), onHide)
            PanelRow(stringResource(R.string.menu_app_info), onInfo)
        }
    }
    RequestFocusOnce(first)
}

@Composable
fun SettingsDialog(
    sortMode: SortMode,
    hiddenApps: List<AppEntry>,
    onDismiss: () -> Unit,
    onSort: (SortMode) -> Unit,
    onUnhide: (AppEntry) -> Unit,
    onClearUsage: () -> Unit,
    onSystem: () -> Unit,
    onNetwork: () -> Unit,
) {
    val first = remember { FocusRequester() }
    PanelDialog(onDismiss, width = 640) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { PanelTitle(stringResource(R.string.settings_sort)) }
            items(SortMode.entries.toList(), key = { it.name }) { mode ->
                val selected = mode == sortMode
                val label = stringResource(mode.labelRes())
                PanelRow(
                    text = (if (selected) "●  " else "○  ") + label,
                    onClick = { onSort(mode) },
                    modifier = if (mode == SortMode.entries.first()) Modifier.focusRequester(first) else Modifier,
                )
            }

            item { PanelTitle(stringResource(R.string.settings_hidden)) }
            if (hiddenApps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.settings_no_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LauncherColors.TextDim,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
            } else {
                items(hiddenApps, key = { "h:" + it.key }) { app ->
                    PanelRow(stringResource(R.string.settings_show, app.label), { onUnhide(app) })
                }
            }

            item { PanelTitle("") }
            item { PanelRow(stringResource(R.string.settings_clear_usage), onClearUsage) }
            item { PanelRow(stringResource(R.string.settings_network), onNetwork) }
            item { PanelRow(stringResource(R.string.settings_system), onSystem) }

            item { PanelTitle(stringResource(R.string.settings_privacy_title)) }
            item {
                Text(
                    stringResource(R.string.settings_privacy_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
    RequestFocusOnce(first)
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PanelRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
