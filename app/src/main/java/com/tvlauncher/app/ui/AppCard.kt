package com.tvlauncher.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvlauncher.app.data.AppEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)

// Brillo especular de la tarjeta enfocada (estilo tvOS): luz suave desde arriba a la izquierda.
private val SpecularColors = listOf(Color(0x38FFFFFF), Color(0x0AFFFFFF), Color.Transparent)

// La escala crece sobre todo hacia arriba, así el nombre (abajo) queda pegado a la tarjeta.
private const val FOCUS_SCALE = 1.10f

private val CardOrigin = TransformOrigin(0.5f, 0.6f)

// Curva idéntica a Google TV Leanback para respuesta instantánea de foco
private val LeanbackFocusEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)

/**
 * Tarjeta de app estilo tvOS:
 * - Sin borde: el foco se marca levantando la tarjeta (escala) y con un brillo especular.
 * - Sin sombra de elevación: dentro de la capa offscreen de la grilla se dibuja como un recuadro.
 * - El nombre solo aparece bajo la tarjeta enfocada (fundido en graphicsLayer, sin recomponer).
 * - Animación de escala ágil de 90ms con aceleración/frenado Leanback.
 * - D-pad long-press robusto con cancelación de clic al soltar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppEntry,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isMoving: Boolean = false,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onStopMoving: (() -> Unit)? = null,
    onFocused: (AppEntry) -> Unit = {},
    badge: String? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressTriggered by remember { mutableStateOf(false) }

    val animatedScaleState = animateFloatAsState(
        targetValue = if (isMoving) 1.12f else if (isFocused) FOCUS_SCALE else 1.0f,
        animationSpec = tween(durationMillis = 90, easing = LeanbackFocusEasing),
        label = "cardScale",
    )

    // zIndex solo depende de booleanos: leer animatedScale aquí forzaría a recomponer
    // la tarjeta en cada frame de la animación de foco (la escala se aplica en graphicsLayer).
    val currentZIndex = when {
        isMoving -> 3f
        isFocused -> 2f
        else -> 0f
    }

    val interactionSource = remember { MutableInteractionSource() }

    val keyModifier = Modifier.onKeyEvent { keyEvent ->
        if (isMoving) {
            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (keyEvent.key) {
                Key.DirectionLeft -> {
                    onMoveLeft?.invoke()
                    true
                }
                Key.DirectionRight -> {
                    onMoveRight?.invoke()
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> {
                    onStopMoving?.invoke()
                    true
                }
                else -> false
            }
        } else {
            // Soporte para botón físico de menú en el control
            if (keyEvent.key == Key.Menu && keyEvent.type == KeyEventType.KeyDown) {
                onLongClick(app)
                return@onKeyEvent true
            }

            val isActionKey = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
            if (isActionKey) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (longPressJob == null && !longPressTriggered) {
                        longPressJob = scope.launch {
                            delay(450L)
                            longPressTriggered = true
                            onLongClick(app)
                        }
                    }
                    true
                } else if (keyEvent.type == KeyEventType.KeyUp) {
                    val wasTriggered = longPressTriggered
                    longPressJob?.cancel()
                    longPressJob = null
                    longPressTriggered = false
                    if (!wasTriggered) {
                        onClick(app)
                    }
                    true
                } else false
            } else false
        }
    }

    val lifted = isFocused || isMoving

    Column(
        modifier = modifier
            .zIndex(currentZIndex)
            .then(keyModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.hasFocus) onFocused(app)
                    if (!it.isFocused) {
                        longPressJob?.cancel()
                        longPressJob = null
                        longPressTriggered = false
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isMoving) {
                            onStopMoving?.invoke()
                        } else if (!longPressTriggered) {
                            onClick(app)
                        }
                    },
                    onLongClick = {
                        if (!isMoving) {
                            longPressTriggered = true
                            onLongClick(app)
                        }
                    },
                ),
        ) {
            // La escala va en una caja interna: el nodo con foco conserva su tamaño de layout, y el
            // scroll (que sigue los límites del nodo enfocado) no persigue la tarjeta mientras crece.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Lectura diferida: la animación solo invalida la capa (draw), no la composición.
                        val s = animatedScaleState.value
                        scaleX = s
                        scaleY = s
                        transformOrigin = CardOrigin
                    }
                    .clip(CardShape)
                    .background(if (lifted) LauncherColors.CardFocused else LauncherColors.Card)
                    .drawWithContent {
                        drawContent()
                        if (lifted) {
                            drawRect(Brush.linearGradient(SpecularColors, Offset.Zero, Offset(size.width * 0.7f, size.height)))
                        }
                    }
,
            ) {
            AppImage(app)
            
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0x66000000), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (isMoving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x334C8DFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "◀   ▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, top = 14.dp)
                .fillMaxWidth()
                .height(22.dp)
                .graphicsLayer {
                    // Aparece junto con la escala: 0 en reposo, 1 con el foco completo.
                    alpha = ((animatedScaleState.value - 1f) / (FOCUS_SCALE - 1f)).coerceIn(0f, 1f)
                },
        )
    }
}

@Composable
private fun AppImage(app: AppEntry) {
    val loader = LocalIconLoader.current
    val icon by produceState(initialValue = loader.peek(app.key), app.key) {
        if (value == null) value = loader.get(app)
    }
    val img = icon
    when {
        img == null -> Box(Modifier.fillMaxSize())
        img.isBanner -> Image(
            bitmap = img.image,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        else -> Box(
            Modifier
                .fillMaxSize()
                .background(LauncherColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = img.image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight(0.62f)
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
