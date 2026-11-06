package com.tvlauncher.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvlauncher.app.R
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.data.SortMode

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
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onStartMove: () -> Unit,
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
                stringResource(if (isPinned) R.string.menu_unpin else R.string.menu_pin),
                onTogglePin,
            )
            if (isPinned) {
                PanelRow(stringResource(R.string.menu_move), onStartMove)
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
    isDefaultLauncher: Boolean,
    onDismiss: () -> Unit,
    onSort: (SortMode) -> Unit,
    onUnhide: (AppEntry) -> Unit,
    onClearUsage: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenKeyMapping: () -> Unit,
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
            item {
                PanelRow(
                    text = stringResource(
                        if (isDefaultLauncher) R.string.settings_default_launcher_yes
                        else R.string.settings_default_launcher_no
                    ),
                    onClick = onSetDefaultLauncher,
                )
            }
            item { PanelRow(stringResource(R.string.settings_key_mapping), onOpenKeyMapping) }
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
fun KeyMappingDialog(
    allApps: List<AppEntry>,
    currentMappings: Map<Int, String>,
    onDismiss: () -> Unit,
    onSaveMapping: (Int, String) -> Unit,
    onRemoveMapping: (Int) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var capturedKeyCode by remember { mutableIntStateOf(0) }
    var capturedKeyName by remember { mutableStateOf("") }
    var showDisclosure by remember { mutableStateOf(false) }

    PanelDialog(onDismiss, width = 640) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        val code = keyEvent.nativeKeyEvent.keyCode
                        // No capturar teclas esenciales de navegación del diálogo
                        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_DPAD_CENTER &&
                            code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN &&
                            code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT
                        ) {
                            capturedKeyCode = code
                            capturedKeyName = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                            return@onKeyEvent true
                        }
                    }
                    false
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.key_mapping_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )

            if (capturedKeyCode == 0) {
                Text(
                    text = stringResource(R.string.key_mapping_listening),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.key_mapping_btn_detected, capturedKeyName, capturedKeyCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.key_mapping_assign_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextDim,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        PanelRow(
                            text = "🏠  " + stringResource(R.string.key_mapping_action_home),
                            onClick = {
                                onSaveMapping(capturedKeyCode, "HOME")
                                capturedKeyCode = 0
                            },
                            modifier = Modifier.focusRequester(first),
                        )
                    }
                    items(allApps, key = { "map_app_" + it.key }) { app ->
                        PanelRow(
                            text = "▶  " + stringResource(R.string.key_mapping_action_app, app.label),
                            onClick = {
                                onSaveMapping(capturedKeyCode, "APP:" + app.packageName)
                                capturedKeyCode = 0
                            },
                        )
                    }
                    item {
                        PanelRow(
                            text = "✖  " + stringResource(R.string.key_mapping_delete),
                            onClick = {
                                onRemoveMapping(capturedKeyCode)
                                capturedKeyCode = 0
                            },
                        )
                    }
                }
            }

            PanelTitle(stringResource(R.string.key_mapping_current))
            if (currentMappings.isEmpty()) {
                Text(
                    text = stringResource(R.string.key_mapping_no_mappings),
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextDim,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(currentMappings.entries.toList(), key = { it.key }) { entry ->
                        val keyName = KeyEvent.keyCodeToString(entry.key).removePrefix("KEYCODE_")
                        val actionDesc = when {
                            entry.value == "HOME" -> stringResource(R.string.key_mapping_action_home)
                            entry.value.startsWith("APP:") -> {
                                val pkg = entry.value.removePrefix("APP:")
                                allApps.find { it.packageName == pkg }?.label ?: pkg
                            }
                            else -> entry.value
                        }
                        PanelRow(
                            text = stringResource(R.string.key_mapping_row, keyName, entry.key, actionDesc),
                            onClick = { onRemoveMapping(entry.key) },
                        )
                    }
                }
            }

            PanelRow(
                text = "⚙  " + stringResource(R.string.key_mapping_service_enable),
                // Política de Play: aviso destacado + consentimiento explícito ANTES de ir a Ajustes.
                onClick = { showDisclosure = true },
            )
        }
    }
    RequestFocusOnce(first)

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                onOpenAccessibilitySettings()
            },
            onDecline = { showDisclosure = false },
        )
    }
}

/**
 * Aviso destacado (prominent disclosure) exigido por Google Play para usar la API de
 * AccessibilityService con fines que no son de accesibilidad. Debe mostrarse dentro de la app,
 * explicar qué hace el servicio y qué datos toca, y requerir una acción afirmativa del usuario.
 */
@Composable
private fun AccessibilityDisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val first = remember { FocusRequester() }
    PanelDialog(onDecline, width = 640) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.a11y_disclosure_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.a11y_disclosure_text),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PanelRow(
                text = stringResource(R.string.a11y_disclosure_decline),
                onClick = onDecline,
                modifier = Modifier.focusRequester(first),
            )
            PanelRow(stringResource(R.string.a11y_disclosure_accept), onAccept)
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
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun PanelRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    var keyPressedDownWhileFocused by remember { mutableStateOf(false) }
    val openTime = remember { System.currentTimeMillis() }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) keyPressedDownWhileFocused = false
            }
            // Preview: clickable consume la tecla antes que un onKeyEvent externo. Se descartan aquí
            // las repeticiones y el soltar de una pulsación larga que empezó antes de abrir el panel.
            .onPreviewKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                            keyPressedDownWhileFocused = true
                        }
                    }
                    return@onPreviewKeyEvent !keyPressedDownWhileFocused
                }
                false
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (System.currentTimeMillis() - openTime >= 350) {
                        onClick()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isFocused) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
