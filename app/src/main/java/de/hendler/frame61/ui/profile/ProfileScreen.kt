package de.hendler.frame61.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * Each profile is its own namespace: resume, recommendations and allowed
 * sources are keyed by profileId.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onProfileChosen: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text = "Profil wählen", style = MaterialTheme.typography.headlineSmall)

        state.profiles.forEach { profile ->
            Surface(
                onClick = {
                    viewModel.select(profile.id)
                    onProfileChosen(profile.id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 4.dp)
                    .tvFocusable(id = "profile-${profile.id}", onClick = {
                        viewModel.select(profile.id)
                        onProfileChosen(profile.id)
                    })
                    .semantics { contentDescription = "Profil ${profile.name}" }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name + if (profile.pinHash.isNotBlank()) "  ·  PIN" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                .tvFocusable(id = "profile-name", onClick = { })
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN (optional)") },
            modifier = Modifier.fillMaxWidth().tvFocusable(id = "profile-pin", onClick = { })
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
}