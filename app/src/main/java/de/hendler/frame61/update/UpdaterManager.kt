package de.hendler.frame61.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Sideload updater for Fire TV / sideloaded installs.
 *
 * Deliberately dependency-free: [java.net.HttpURLConnection] instead of a HTTP
 * client, [FileProvider] instead of raw file URIs. That keeps the APK small and
 * avoids Play-only APIs (the app is not distributed through Play).
 *
 * Flow: `check(manifestUrl)` reads `version.json`, compares `versionCode` with
 * the installed one. `downloadAndInstall` fetches the APK to internal storage,
 * verifies the SHA-256 when the manifest carries one, then hands the file to
 * the system installer via a content URI.
 */
class UpdaterManager(
    private val context: Context,
    private val currentVersionCode: Int
) {

    suspend fun check(manifestUrl: String): UpdateCheck = withContext(Dispatchers.IO) {
        val url = manifestUrl.trim()
        if (url.isBlank()) return@withContext UpdateCheck.Failed("Keine Update-Quelle gesetzt")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext UpdateCheck.Failed("Update-Quelle muss eine http(s)-URL sein")
        }
        val text = runCatching { httpGet(url) }.getOrElse {
            return@withContext UpdateCheck.Failed("Update-Quelle nicht erreichbar: ${it.message}")
        }
        val manifest = UpdateManifest.parse(text)
            ?: return@withContext UpdateCheck.Failed("Update-Manifest ungültig")
        if (manifest.versionCode <= currentVersionCode) return@withContext UpdateCheck.UpToDate
        return@withContext UpdateCheck.Available(manifest)
    }

    /** Downloads the APK, verifies the hash, opens the system installer. */
    suspend fun downloadAndInstall(manifest: UpdateManifest): UpdateInstall =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val file = File(dir, "frame61-${manifest.versionCode}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = runCatching {
                val conn = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                    }
                }
                conn.responseCode
            }.getOrElse { return@withContext UpdateInstall.Failed("Download fehlgeschlagen: ${it.message}") }

            val expected = manifest.sha256?.lowercase()
            if (expected != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    file.delete()
                    return@withContext UpdateInstall.Failed("SHA-256 passt nicht — APK verworfen")
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                return@withContext UpdateInstall.Failed("Installer konnte nicht geöffnet werden: ${it.message}")
            }
            UpdateInstall.Installed
        }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}