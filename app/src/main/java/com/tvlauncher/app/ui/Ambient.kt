package com.tvlauncher.app.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvlauncher.app.R
import com.tvlauncher.app.data.AppEntry
import com.tvlauncher.app.data.Usage

private val BaseColor = Color(0xFF0A0D14)
private val DefaultTint = Color(0xFF2B3654)

/**
 * Fondo ambiental compartido: el color de la app enfocada pintado en un bitmap chico.
 *
 * Rendimiento (GPU Mali-450 del Mi Box 4): dos degradados radiales a 1920x1080 en cada frame
 * saturaban la GPU. Se pintan en un bitmap de 160x90 (en CPU, solo cuando cambia el color)
 * y se dibujan escalados con filtrado bilineal: un único quad texturizado por frame.
 * La grilla reutiliza el mismo bitmap para fundir sus bordes ([fadingEdges]).
 */
@Stable
class Ambient internal constructor(internal val tint: State<Color>) {
    internal val bitmap = ImageBitmap(AMBIENT_W, AMBIENT_H)
    internal val canvasScope = CanvasDrawScope()
    /** Tamaño en pantalla del fondo (raíz); lo escribe [AmbientBackground] al dibujar. */
    internal var screenSize = Size.Zero
}

@Composable
fun rememberAmbient(focused: State<AppEntry?>): Ambient {
    val loader = LocalIconLoader.current
    val key = focused.value?.key
    val target = remember(key) { key?.let { loader.peek(it)?.tint }?.let { Color(it) } ?: DefaultTint }
    val tint = animateColorAsState(target, tween(320), label = "ambientTint")
    return remember { Ambient(tint) }
}

@Composable
fun AmbientBackground(ambient: Ambient, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                // Leer tint aquí hace que el caché se reconstruya solo cuando cambia el color.
                val t = ambient.tint.value
                val small = Size(AMBIENT_W.toFloat(), AMBIENT_H.toFloat())
                ambient.canvasScope.draw(this, layoutDirection, Canvas(ambient.bitmap), small) {
                    drawRect(BaseColor)
                    drawRect(
                        Brush.radialGradient(
                            listOf(t.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(small.width * 0.78f, -small.height * 0.1f),
                            radius = small.width * 0.8f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(t.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(0f, small.height),
                            radius = small.width * 0.6f,
                        ),
                    )
                }
                ambient.screenSize = size
                val dst = IntSize(size.width.toInt(), size.height.toInt())
                onDrawBehind {
                    drawImage(ambient.bitmap, dstSize = dst, filterQuality = FilterQuality.Low)
                }
            },
    )
}

/**
 * Funde las filas que entran o salen por arriba y abajo de la grilla, sin capa offscreen:
 * encima de cada borde se dibuja el mismo fondo ambiental en [FADE_STEPS] franjas con opacidad
 * decreciente. Una capa offscreen del tamaño de la grilla duplicaba el trabajo de la GPU
 * (de ~17 a ~34 ms por frame en el Mi Box 4).
 */
fun Modifier.fadingEdges(ambient: Ambient, fade: Dp): Modifier = composed {
    val origin = remember { FloatArray(2) }
    this
        .onGloballyPositioned { c ->
            val p = c.positionInRoot()
            origin[0] = p.x
            origin[1] = p.y
        }
        .drawWithContent {
            drawContent()
            ambient.tint.value // redibuja los bordes cuando cambia el color del fondo
            val screen = ambient.screenSize
            if (screen.width <= 0f) return@drawWithContent
            val sx = AMBIENT_W / screen.width
            val sy = AMBIENT_H / screen.height
            val fadePx = fade.toPx().coerceAtMost(size.height / 2f)
            val h = size.height.toInt()
            for (i in 0 until FADE_STEPS) {
                // Franjas en píxeles enteros y contiguas: sin huecos ni solapes (evita líneas).
                val y0 = (fadePx * i / FADE_STEPS).roundToInt()
                val y1 = (fadePx * (i + 1) / FADE_STEPS).roundToInt()
                if (y1 <= y0) continue
                // Opacidad 1 en el borde, 0 hacia adentro (curva suave).
                val a = 1f - (i + 0.5f) / FADE_STEPS
                val alpha = a * a * (3f - 2f * a)
                drawStrip(ambient, origin, sx, sy, top = y0, height = y1 - y0, alpha = alpha)
                drawStrip(ambient, origin, sx, sy, top = h - y1, height = y1 - y0, alpha = alpha)
            }
        }
}

private fun DrawScope.drawStrip(
    ambient: Ambient,
    origin: FloatArray,
    sx: Float,
    sy: Float,
    top: Int,
    height: Int,
    alpha: Float,
) {
    val srcX = (origin[0] * sx).toInt().coerceIn(0, AMBIENT_W - 1)
    val srcY = ((origin[1] + top) * sy).toInt().coerceIn(0, AMBIENT_H - 1)
    val srcW = (size.width * sx).toInt().coerceIn(1, AMBIENT_W - srcX)
    val srcH = (height * sy).toInt().coerceIn(1, AMBIENT_H - srcY)
    drawImage(
        image = ambient.bitmap,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(0, top),
        dstSize = IntSize(size.width.toInt(), height),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

private const val AMBIENT_W = 160
private const val AMBIENT_H = 90
private const val FADE_STEPS = 24

@Composable
fun FocusedInfo(focused: State<AppEntry?>, usage: Map<String, Usage>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = focused.value
    Column(modifier.fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.Bottom) {
        if (app != null) {
            val u = usage[app.key]
            val meta = remember(app.key, u) {
                if (u == null || u.count == 0) {
                    context.getString(R.string.hero_never)
                } else {
                    val ago = DateUtils.getRelativeTimeSpanString(
                        u.lastLaunch, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    )
                    context.getString(R.string.hero_meta, ago, u.count)
                }
            }
            Text(app.label, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodyLarge, color = LauncherColors.TextDim, maxLines = 1)
        }
    }
}
