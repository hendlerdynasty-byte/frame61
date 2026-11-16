package de.hendler.frame61.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hendler.frame61.design.tvFocusable

/**
 * Profile picker with optional PIN.
 *
 * Each profile is its own namespace: resume, recommendations, home layout and
 * allowed sources are keyed by profileId. The PIN can be set, changed and
 * removed per profile; a profile without a PIN opens directly.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onProfileChosen: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinDialogFor by remember { mutableStateOf<Long?>(null) }
    var pinInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text = "Profil wählen", style = MaterialTheme.typography.headlineSmall)

        state.profiles.forEach { profile ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        viewModel.select(profile.id)
                        onProfileChosen(profile.id)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .tvFocusable(id = "profile-${profile.id}", onClick = {
                            viewModel.select(profile.id)
                            onProfileChosen(profile.id)
                        })
                        .semantics { contentDescription = "Profil ${profile.name}" }
                ) {
                    Text(
                        text = profile.name + if (profile.pinHash.isNotBlank()) "  ·  PIN" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                OutlinedButton(
                    onClick = {
                        pinDialogFor = profile.id
                        pinInput = ""
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .tvFocusable(id = "profile-pin-edit-${profile.id}", onClick = {
                            pinDialogFor = profile.id
                            pinInput = ""
                        })
                ) { Text(text = if (profile.pinHash.isBlank()) "PIN" else "PIN ändern") }
            }
        }

        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth().tvFocusable(id = "profile-name", onClick = {})
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN (optional)") },
            modifier = Modifier.fillMaxWidth().tvFocusable(id = "profile-pin", onClick = {})
        )
        Button(
            onClick = {
                viewModel.create(name, pin)
                name = ""
                pin = ""
            },
            modifier = Modifier.padding(top = 8.dp).tvFocusable(id = "profile-create", onClick = {
                viewModel.create(name, pin)
                name = ""
                pin = ""
            })
        ) {
            Text(text = "Profil anlegen")
        }
    }

    val dialogProfileId = pinDialogFor
    if (dialogProfileId != null) {
        val hasPin = state.profiles.firstOrNull { it.id == dialogProfileId }?.pinHash?.isNotBlank() == true
        AlertDialog(
            onDismissRequest = { pinDialogFor = null },
            title = { Text(text = "PIN für Profil $dialogProfileId") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Neue PIN (leer = entfernen)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPin(dialogProfileId, pinInput)
                    pinDialogFor = null
                }) { Text(text = "Speichern") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hasPin) {
                        TextButton(onClick = {
                            viewModel.removePin(dialogProfileId)
                            pinDialogFor = null
                        }) { Text(text = "Entfernen") }
                    }
                    TextButton(onClick = { pinDialogFor = null }) { Text(text = "Abbrechen") }
                }
            }
        )
    }
}