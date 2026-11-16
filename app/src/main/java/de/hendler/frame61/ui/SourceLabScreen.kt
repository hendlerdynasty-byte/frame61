package de.hendler.frame61.ui

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
import de.hendler.frame61.scraper.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceLabViewModel @Inject constructor(
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceLabUiState())
    val uiState: StateFlow<SourceLabUiState> = _uiState.asStateFlow()

    fun loadPacks() {
        viewModelScope.launch {
            val packs = packLoader.loadPacks()
            _uiState.value = _uiState.value.copy(availablePacks = packs)
        }
    }

    fun importFromDownloads() {
        viewModelScope.launch {
            // TODO: Implementiere Download-Ordner-Import
            _uiState.value = _uiState.value.copy(message = "Import from Downloads not yet implemented")
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            // TODO: Implementiere URL-Import mit sha256-Prüfung
            _uiState.value = _uiState.value.copy(message = "Import from URL not yet implemented")
        }
    }

    fun testPack(pack: SourcePack) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = "Testing ${pack.name}...")

            try {
                // Simpler Test: Suche nach "test"
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

    fun exportPack(pack: SourcePack) {
        viewModelScope.launch {
            // TODO: Implementiere Export
            _uiState.value = _uiState.value.copy(message = "Export not yet implemented")
        }
    }
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

        // URL-Feld
        var urlText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Manifest URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.importFromUrl(urlText) },
            enabled = urlText.isNotBlank()
        ) {
            Text("Import from URL")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.importFromDownloads() }
        ) {
            Text("Import from Downloads")
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