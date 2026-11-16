package de.hendler.frame61.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.design.FocusRestorer
import de.hendler.frame61.design.Frame61Theme
import de.hendler.frame61.design.ProvideTvFocusRegistry
import de.hendler.frame61.design.tvFocusable

@AndroidEntryPoint
class DetailActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val composeContent = androidx.compose.ui.platform.ComposeView(this)
        setContentView(composeContent)
        composeContent.setContent {
            val titleId = intent?.getStringExtra("titleId").orEmpty()
            Frame61Theme {
                ProvideTvFocusRegistry {
                    DetailScreenComposable(titleId)
                }
            }
        }
    }
}

@Composable
fun DetailScreenComposable(titleId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .tvFocusable(id = "detail-activity-card", onClick = { })
        ) {
            Text(text = "Detail: $titleId", modifier = Modifier.padding(16.dp))
        }
    }
    FocusRestorer(targetId = "detail-activity-card")
}