package com.tvlauncher.app.data

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Immutable
class IconImage(val image: ImageBitmap, val isBanner: Boolean, val bytes: Int)

/**
 * Decodifica iconos/banners FUERA del hilo principal, con prioridad de hilo BACKGROUND y a tamaño
 * exacto de pantalla (nunca se decodifican bitmaps gigantes). En API 26+ se suben a la GPU como
 * bitmaps HARDWARE desde el hilo de fondo, así el primer dibujo no cuesta tiempo en el render thread.
 * Caché LRU acotada por memoria. Las celdas hacen `peek` síncrono (sin parpadeo si ya está en caché).
 */
class IconLoader(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
) {
    private val dispatcher = Executors.newFixedThreadPool(2) { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "icon-decoder").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val cache = object : LruCache<String, IconImage>(cacheBytes()) {
        override fun sizeOf(key: String, value: IconImage): Int = value.bytes.coerceAtLeast(1)
    }
    private val inflight = ConcurrentHashMap<String, Deferred<IconImage>>()
    private var preloadJob: Job? = null

    fun peek(key: String): IconImage? = cache.get(key)

    suspend fun get(app: AppEntry): IconImage {
        cache.get(app.key)?.let { return it }
        val deferred = synchronized(inflight) {
            inflight[app.key] ?: scope.async(dispatcher, start = CoroutineStart.LAZY) {
                decode(app).also { cache.put(app.key, it) }
            }.also { d ->
                inflight[app.key] = d
                d.invokeOnCompletion { inflight.remove(app.key, d) }
                d.start()
            }
        }
        return deferred.await()
    }

    /** Precarga en orden de pantalla (lo visible primero). Cancela la precarga anterior. */
    fun preload(apps: List<AppEntry>) {
        preloadJob?.cancel()
        preloadJob = scope.launch(dispatcher) {
            for (app in apps) {
                ensureActive()
                if (cache.get(app.key) == null) runCatching { get(app) }
            }
        }
    }

    fun evict(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    private fun decode(app: AppEntry): IconImage {
        val component = app.component
        val banner: Drawable? = if (app.isLeanback) {
            runCatching { pm.getActivityBanner(component) }.getOrNull()
        } else null

        return if (banner != null) {
            val bmp = render(banner, BANNER_W, BANNER_H)
            IconImage(bmp.asImageBitmap(), isBanner = true, bytes = BANNER_W * BANNER_H * 4)
        } else {
            val icon = runCatching { pm.getActivityIcon(component) }.getOrElse { pm.defaultActivityIcon }
            val bmp = render(icon, ICON_SIZE, ICON_SIZE)
            IconImage(bmp.asImageBitmap(), isBanner = false, bytes = ICON_SIZE * ICON_SIZE * 4)
        }
    }

    private fun render(drawable: Drawable, w: Int, h: Int): Bitmap {
        val soft = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(soft))
        if (Build.VERSION.SDK_INT >= 26) {
            val hw = runCatching { soft.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
            if (hw != null) {
                soft.recycle()
                return hw
            }
        }
        return soft
    }

    private companion object {
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON_SIZE = 160

        fun cacheBytes(): Int {
            val max = Runtime.getRuntime().maxMemory()
            return (max / 6).coerceIn(16L * 1024 * 1024, 48L * 1024 * 1024).toInt()
        }
    }
}
