package com.tvlauncher.app.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

enum class SortMode { NAME, MOST_USED, RECENT }

@Immutable
data class Usage(val count: Int, val lastLaunch: Long)

@Immutable
data class StoreSnapshot(
    val sortMode: SortMode = SortMode.NAME,
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val usage: Map<String, Usage> = emptyMap(),
    val keyMappings: Map<Int, String> = emptyMap(),
)

/**
 * Único almacenamiento del launcher: archivo privado de la app (DataStore), local,
 * excluido de backups. Guarda orden, favoritas, ocultas y contadores de uso. Nada sale del dispositivo.
 */
class LauncherStore(context: Context) {
    private val ds = context.applicationContext.launcherDataStore

    val snapshot: Flow<StoreSnapshot> = ds.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSnapshot() }
        .distinctUntilChanged()

    suspend fun setSort(mode: SortMode) {
        ds.edit { it[SORT] = mode.name }
    }

    suspend fun recordLaunch(key: String) {
        ds.edit { p ->
            val ck = intPreferencesKey(COUNT_PREFIX + key)
            p[ck] = (p[ck] ?: 0) + 1
            p[longPreferencesKey(TIME_PREFIX + key)] = System.currentTimeMillis()
        }
    }

    suspend fun togglePinned(key: String) {
        ds.edit { p ->
            val cur = p[PINNED].decodeList()
            p[PINNED] = (if (key in cur) cur - key else cur + key).joinToString("\n")
        }
    }

    suspend fun movePinned(key: String, delta: Int) {
        ds.edit { p ->
            val list = p[PINNED].decodeList().toMutableList()
            val i = list.indexOf(key)
            val j = i + delta
            if (i >= 0 && j in list.indices) {
                list[i] = list[j]
                list[j] = key
                p[PINNED] = list.joinToString("\n")
            }
        }
    }

    suspend fun hide(key: String) {
        ds.edit { p ->
            p[HIDDEN] = (p[HIDDEN] ?: emptySet()) + key
            p[PINNED] = p[PINNED].decodeList().minus(key).joinToString("\n")
        }
    }

    suspend fun unhide(key: String) {
        ds.edit { p -> p[HIDDEN] = (p[HIDDEN] ?: emptySet()) - key }
    }

    suspend fun setKeyMapping(keyCode: Int, action: String) {
        ds.edit { p ->
            val cur = p[KEY_MAPPINGS]?.lines()?.filter { it.contains(":") }?.toMutableList() ?: mutableListOf()
            cur.removeAll { it.startsWith("$keyCode:") }
            cur.add("$keyCode:$action")
            p[KEY_MAPPINGS] = cur.joinToString("\n")
        }
    }

    suspend fun removeKeyMapping(keyCode: Int) {
        ds.edit { p ->
            val cur = p[KEY_MAPPINGS]?.lines()?.filter { it.contains(":") }?.toMutableList() ?: mutableListOf()
            cur.removeAll { it.startsWith("$keyCode:") }
            p[KEY_MAPPINGS] = cur.joinToString("\n")
        }
    }

    suspend fun clearUsage() {
        ds.edit { p ->
            val names = p.asMap().keys.map { it.name }
                .filter { it.startsWith(COUNT_PREFIX) || it.startsWith(TIME_PREFIX) }
            names.forEach { n ->
                p.remove(intPreferencesKey(n))
                p.remove(longPreferencesKey(n))
            }
        }
    }

    private fun Preferences.toSnapshot(): StoreSnapshot {
        val sort = this[SORT]?.let { n -> SortMode.entries.firstOrNull { it.name == n } } ?: SortMode.NAME
        val counts = HashMap<String, Int>()
        val times = HashMap<String, Long>()
        for ((k, v) in asMap()) {
            val n = k.name
            when {
                n.startsWith(COUNT_PREFIX) && v is Int -> counts[n.substring(COUNT_PREFIX.length)] = v
                n.startsWith(TIME_PREFIX) && v is Long -> times[n.substring(TIME_PREFIX.length)] = v
            }
        }
        val usage = HashMap<String, Usage>(counts.size)
        for ((k, c) in counts) usage[k] = Usage(c, times[k] ?: 0L)

        val mappings = HashMap<Int, String>()
        this[KEY_MAPPINGS]?.lines()?.forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0].toIntOrNull()?.let { code ->
                    mappings[code] = parts[1]
                }
            }
        }

        return StoreSnapshot(
            sortMode = sort,
            pinned = this[PINNED].decodeList(),
            hidden = this[HIDDEN] ?: emptySet(),
            usage = usage,
            keyMappings = mappings,
        )
    }

    private fun String?.decodeList(): List<String> =
        this?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    private companion object {
        const val COUNT_PREFIX = "c|"
        const val TIME_PREFIX = "t|"
        val SORT = stringPreferencesKey("sort")
        val PINNED = stringPreferencesKey("pinned")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val KEY_MAPPINGS = stringPreferencesKey("key_mappings")
    }
}
