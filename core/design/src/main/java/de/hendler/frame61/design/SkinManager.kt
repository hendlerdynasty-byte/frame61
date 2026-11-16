package de.hendler.frame61.design

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads skins from `assets/skins/<id>.json`.
 *
 * The JSON files are the single source of truth. [DefaultAssetSkinManager] parses
 * all 11 files and falls back to [DefaultFrame61Skin] per file, so one broken file
 * cannot break the theme.
 */
interface SkinManager {
    suspend fun getSkin(id: String): Frame61Skin
    suspend fun getAllSkins(): List<Frame61Skin>
    suspend fun getAvailableSkins(): List<SkinDefinition>
    suspend fun reload()
}

data class SkinDefinition(
    val id: String,
    val name: String,
    val canvas: String,
    val accents: String
)

class DefaultAssetSkinManager(
    private val context: Context,
    private val parser: SkinJsonParser = SkinJsonParser()
) : SkinManager {

    @Volatile
    private var cache: List<Frame61Skin>? = null

    override suspend fun getSkin(id: String): Frame61Skin = withContext(Dispatchers.IO) {
        loadAll().firstOrNull { it.id == id }
            ?: DefaultFrame61Skin.copy(id = id, name = SkinJsonParser.displayName(id))
    }

    override suspend fun getAllSkins(): List<Frame61Skin> = withContext(Dispatchers.IO) { loadAll() }

    override suspend fun getAvailableSkins(): List<SkinDefinition> = withContext(Dispatchers.IO) {
        loadAll().map { it.toDefinition() }
    }

    override suspend fun reload() {
        cache = null
    }

    private fun loadAll(): List<Frame61Skin> {
        cache?.let { return it }
        val assets = context.assets
        val files = assets.list("skins")
            ?.filter { it.endsWith(".json") && !it.startsWith("._") }
            ?.sorted()
            ?: emptyList()

        val loaded = files.mapNotNull { file ->
            runCatching {
                assets.open("skins/$file").bufferedReader().use { parser.parse(it.readText()) }
            }.getOrNull()
        }

        return loaded.also { cache = it }
    }
}

/**
 * Loads skins from the file system — used where no Android `Context` exists
 * (unit tests, Source Lab preview).
 */
class FileSkinManager(
    private val skinsDir: File,
    private val parser: SkinJsonParser = SkinJsonParser()
) : SkinManager {

    override suspend fun getSkin(id: String): Frame61Skin =
        loadAll().firstOrNull { it.id == id }
            ?: DefaultFrame61Skin.copy(id = id, name = SkinJsonParser.displayName(id))

    override suspend fun getAllSkins(): List<Frame61Skin> = loadAll()

    override suspend fun getAvailableSkins(): List<SkinDefinition> =
        loadAll().map { it.toDefinition() }

    override suspend fun reload() = Unit

    fun loadAll(): List<Frame61Skin> =
        skinsDir.listFiles { f -> f.isFile && f.extension == "json" && !f.name.startsWith("._") }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> runCatching { parser.parse(file.readText()) }.getOrNull() }
            ?: emptyList()
}

/** Parses one skin from a JSON string — used by the Source Lab preview. */
fun parseSkin(content: String): Frame61Skin? = SkinJsonParser().parse(content)

internal fun Frame61Skin.toDefinition(): SkinDefinition = SkinDefinition(
    id = id,
    name = name,
    canvas = colors.canvas.toHex(),
    accents = colors.primary.toHex()
)

internal fun Color.toHex(): String = String.format("#%06X", (toArgbColor() and 0xFFFFFF))

private fun Color.toArgbColor(): Int {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}