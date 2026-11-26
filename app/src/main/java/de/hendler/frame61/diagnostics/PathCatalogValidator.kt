package de.hendler.frame61.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Pure validation of the fixed Aniworld path-acceptance manifest. */
object PathCatalogValidator {
    const val SOURCE_ID = "aniworld"
    const val EXPECTED_COUNT = 20
    const val ID_PREFIX = "aw:"

    data class Issue(val code: String, val id: String?, val message: String)

    data class Report(
        val sourceId: String,
        val ids: List<String>,
        val issues: List<Issue>
    ) {
        val isValid: Boolean get() = issues.isEmpty()
    }

    /**
     * Validates JSON structure, the exact sample contract and catalog identity.
     * No Android, network or filesystem access belongs in this class.
     */
    fun validate(manifestJson: String, catalogJson: String): Report {
        val json = Json { ignoreUnknownKeys = true }
        val manifest = runCatching { json.decodeFromString<ManifestDocument>(manifestJson) }
            .getOrNull()
        val catalog = runCatching { json.decodeFromString<CatalogDocument>(catalogJson) }
            .getOrNull()
        if (manifest == null || catalog == null) {
            return Report(
                sourceId = manifest?.sourceId.orEmpty(),
                ids = manifest?.ids.orEmpty(),
                issues = buildList {
                    if (manifest == null) add(Issue("MANIFEST_JSON", null, "Manifest ist kein gültiges JSON"))
                    if (catalog == null) add(Issue("CATALOG_JSON", null, "Katalog ist kein gültiges JSON"))
                }
            )
        }

        val issues = mutableListOf<Issue>()
        if (manifest.schema != 1) {
            issues += Issue("MANIFEST_SCHEMA", null, "schema=${manifest.schema}, erwartet=1")
        }
        if (manifest.sourceId != SOURCE_ID) {
            issues += Issue("MANIFEST_SOURCE", null, "sourceId=${manifest.sourceId}, erwartet=$SOURCE_ID")
        }
        if (manifest.expectedCount != EXPECTED_COUNT) {
            issues += Issue(
                "MANIFEST_COUNT",
                null,
                "expectedCount=${manifest.expectedCount}, erwartet=$EXPECTED_COUNT"
            )
        }
        if (manifest.ids.size != EXPECTED_COUNT) {
            issues += Issue("SAMPLE_COUNT", null, "${manifest.ids.size} IDs, erwartet=$EXPECTED_COUNT")
        }

        val counts = manifest.ids.groupingBy { it }.eachCount()
        manifest.ids.forEachIndexed { index, id ->
            when {
                !id.startsWith(ID_PREFIX) || id.length <= ID_PREFIX.length ->
                    issues += Issue("ID_FORMAT", id, "ID ${index + 1} ist keine aw:-ID")
                counts.getValue(id) > 1 ->
                    issues += Issue("ID_DUPLICATE", id, "ID kommt mehrfach vor")
                else -> {
                    val entry = catalog.items.firstOrNull { it.id == id }
                    when {
                        entry == null -> issues += Issue("CATALOG_MISSING", id, "Kein Katalogeintrag")
                        entry.group != SOURCE_ID ->
                            issues += Issue("CATALOG_GROUP", id, "group=${entry.group}, erwartet=$SOURCE_ID")
                        entry.sourceKey != id.removePrefix(ID_PREFIX) ->
                            issues += Issue("CATALOG_SOURCE_KEY", id, "sourceKey=${entry.sourceKey}")
                        entry.title.isBlank() ->
                            issues += Issue("CATALOG_TITLE", id, "Titel ist leer")
                    }
                }
            }
        }
        return Report(manifest.sourceId, manifest.ids.toList(), issues)
    }

    @Serializable
    private data class ManifestDocument(
        val schema: Int = 0,
        val sourceId: String = "",
        val expectedCount: Int = 0,
        val ids: List<String> = emptyList()
    )

    @Serializable
    private data class CatalogDocument(val items: List<CatalogEntry> = emptyList())

    @Serializable
    private data class CatalogEntry(
        val id: String = "",
        val title: String = "",
        val group: String? = null,
        val sourceKey: String? = null
    )
}
