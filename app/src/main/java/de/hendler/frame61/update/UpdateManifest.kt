package de.hendler.frame61.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Update manifest as published next to the APK.
 *
 * Example (`update.json`):
 * ```json
 * {"versionCode": 2, "versionName": "1.1.0",
 *  "apkUrl": "https://example.invalid/frame61-2.apk",
 *  "sha256": "…", "notes": "Was neu ist"}
 * ```
 *
 * [parse] is pure and unit-tested; a malformed manifest returns null instead of
 * throwing, so a broken update server can never crash the app.
 */
@Serializable
data class UpdateManifestDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String? = null,
    val notes: String? = null
)

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null,
    val notes: String? = null
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** Parses a manifest; blank/unknown fields make it invalid, never fatal. */
        fun parse(text: String): UpdateManifest? {
            val dto = runCatching { json.decodeFromString<UpdateManifestDto>(text) }.getOrNull()
                ?: return null
            if (dto.versionCode <= 0 || dto.apkUrl.isBlank()) return null
            if (!dto.apkUrl.startsWith("http://") && !dto.apkUrl.startsWith("https://")) return null
            return UpdateManifest(
                versionCode = dto.versionCode,
                versionName = dto.versionName.ifBlank { dto.versionCode.toString() },
                apkUrl = dto.apkUrl,
                sha256 = dto.sha256?.takeIf { it.isNotBlank() },
                notes = dto.notes?.takeIf { it.isNotBlank() }
            )
        }
    }
}

/** Outcome of an update check — no exception ever escapes to the UI. */
sealed class UpdateCheck {
    object UpToDate : UpdateCheck()
    data class Available(val manifest: UpdateManifest) : UpdateCheck()
    data class Failed(val message: String) : UpdateCheck()
}

/** Outcome of a download+install attempt. */
sealed class UpdateInstall {
    object Installed : UpdateInstall()
    data class Failed(val message: String) : UpdateInstall()
}