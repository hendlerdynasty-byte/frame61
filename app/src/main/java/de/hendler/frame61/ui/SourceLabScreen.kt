package de.hendler.frame61.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.scraper.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Source Lab ViewModel — the tool for adding source packs without rebuilding.
 *
 * Import: a pack picked from Downloads (SAF) or fetched from a manifest URL
 * with optional SHA-256 verification. Both land in the internal `sources/`
 * directory and are validated by the same [PackLoaderValidator] that guards the
 * bundled packs; a broken pack is rejected and the app keeps running.
 *
 * Export: writes the selected pack to the app's Downloads folder as JSON.
 */
@HiltViewModel
class SourceLabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter
) : ViewModel() {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val _uiState = MutableStateFlow(SourceLabUiState())
    val uiState: StateFlow<SourceLabUiState> = _uiState.asStateFlow()

    fun loadPacks() {
        viewModelScope.launch {
            val packs = packLoader.loadAllPacks()
            _uiState.value = _uiState.value.copy(availablePacks = packs)
        }
    }

    /** Reads a content:// uri (SAF pick from Downloads) and imports the pack. */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Lese Datei …")
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Datei nicht lesbar")
                return@launch
            }
            storeImported(content, "Datei")
        }
    }

    /** Fetches a pack from a URL; optional expected SHA-256 rejects tampered files. */
    fun importFromUrl(url: String, expectedSha: String) {
        viewModelScope.launch {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "URL muss http(s) sein")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Lade …")
            val bytes = runCatching { httpGetBytes(trimmed) }.getOrElse {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Download fehlgeschlagen: ${it.message}")
                return@launch
            }
            val actualSha = sha256Hex(bytes)
            val expected = expectedSha.trim().lowercase()
            if (expected.isNotEmpty() && actualSha != expected) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "SHA-256 passt nicht ($actualSha) — verworfen"
                )
                return@launch
            }
            val content = String(bytes, Charsets.UTF_8)
            storeImported(content, "URL", actualSha)
        }
    }

    private suspend fun storeImported(content: String, source: String, sha: String? = null) {
        val pack = PackLoaderValidator.parse(content)
        if (pack == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, message = "Pack ungültig — verworfen")
            return
        }
        val written = runCatching {
            val dir = packLoader.internalSourcesDir().apply { mkdirs() }
            File(dir, "${safeFileName(pack.id)}.json").writeText(content)
        }.isSuccess
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            message = if (written) {
                "Importiert: ${pack.name}" + (sha?.let { " (sha256 $it)" } ?: "")
            } else {
                "Import fehlgeschlagen: ${pack.name}"
            }
        )
        loadPacks()
    }

    fun exportPack(pack: SourcePack) {
        viewModelScope.launch {
            val text = runCatching { json.encodeToString(SourcePack.serializer(), pack) }.getOrElse {
                _uiState.value = _uiState.value.copy(message = "Export fehlgeschlagen: ${it.message}")
                return@launch
            }
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val file = File(base, "${safeFileName(pack.id)}.json")
            runCatching { file.writeText(text) }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(message = "Exportiert: ${file.absolutePath}")
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(message = "Export fehlgeschlagen: ${it.message}")
                }
            )
        }
    }

    fun testPack(pack: SourcePack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Testing ${pack.name}...")
            try {
                val results = interpreter.search(pack, "test")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Found ${results.size} results for ${pack.name}",
                    lastTestResults = results
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error testing ${pack.name}: ${e.message}"
                )
            }
        }
    }

    private fun httpGetBytes(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun safeFileName(id: String): String =
        id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }.joinToString("")
}

data class SourceLabUiState(
    val availablePacks: List<SourcePack> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = "",
    val lastTestResults: List<SearchResult> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLabScreen(
    viewModel: SourceLabViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPacks()
    }

    // Downloads/Dateien: SAF pick. On TV most launchers ship no file manager —
    // the URL import is the primary path there.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Source Lab",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        var urlText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Manifest URL") },
            modifier = Modifier.fillMaxWidth()
        )

        var expectedSha by remember { mutableStateOf("") }
        OutlinedTextField(
            value = expectedSha,
            onValueChange = { expectedSha = it },
            label = { Text("SHA-256 (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.importFromUrl(urlText, expectedSha) },
            enabled = urlText.isNotBlank()
        ) {
            Text("Von URL importieren")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { picker.launch(arrayOf("*/*")) }
        ) {
            Text("Datei aus Downloads wählen")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.message.isNotBlank()) {
            Text(text = uiState.message)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pack-Liste
        Text(
            text = "Available Packs (${uiState.availablePacks.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        uiState.availablePacks.forEach { pack ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ID: ${pack.id} | Kind: ${pack.kind}" +
                            (pack.tier?.let { " | Tier $it" } ?: "") +
                            if (pack.legalFree) " | legal-free" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Base URL: ${pack.baseUrl}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (pack.verified) {
                        Text(
                            text = "✓ Verified",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testPack(pack) }
                        ) {
                            Text("Test")
                        }

                        OutlinedButton(
                            onClick = { viewModel.exportPack(pack) }
                        ) {
                            Text("Export")
                        }
                    }
                }
            }
        }

        // Test-Ergebnisse
        if (uiState.lastTestResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Last Test Results",
                style = MaterialTheme.typography.titleMedium
            )
            uiState.lastTestResults.forEach { result ->
                Text(text = "ID: ${result.id}, Title: ${result.title}")
            }
        }
    }
}