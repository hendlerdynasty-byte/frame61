package de.hendler.frame61.diagnostics

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import de.hendler.frame61.player.Media3Frame61Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Perf-only deterministic proof of the real production engine chain. */
@androidx.annotation.OptIn(UnstableApi::class)
class MediaSelfTestActivity : ComponentActivity() {
    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var statusView: TextView
    private var testJob: Job? = null
    private var renderedEngine = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = Media3Frame61Player(this)
        playerView = PlayerView(this).apply {
            useController = false
            player = engine.exoPlayer
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        playerContainer = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(statusView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(playerContainer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
        setContentView(root)
        de.hendler.frame61.net.PlaybackDiagnostics.beginSession("media-self-test", 0)
        engine.attach()
        testJob = lifecycleScope.launch { runCases() }
    }

    private suspend fun runCases() {
        val results = mutableListOf<String>()
        for (case in CASES) {
            renderedEngine = ""
            status("Starte ${case.label}")
            engine.open(case.url)
            var sawReady = false
            var sawProgress = false
            repeat(10) {
                delay(500)
                val state = engine.state.value
                sawReady = sawReady || state.isReady
                sawProgress = sawProgress || state.positionMs > 0L
                renderEngine(state.engine)
                status(
                    "${case.label} | ${state.engine} | ${state.positionMs}/${state.durationMs} ms | " +
                        (state.error ?: "kein Fehler")
                )
            }
            val state = engine.state.value
            val pass = sawReady && sawProgress && state.error == null
            val result = "${case.label} engine=${state.engine} ready=${state.isReady} " +
                "sawReady=$sawReady sawProgress=$sawProgress " +
                "position=${state.positionMs} duration=${state.durationMs} error=${state.error} " +
                if (pass) "PASS" else "FAIL"
            Log.i(TAG, result)
            results += result
            status(result)
            delay(500)
        }
        status("Selbsttest abgeschlossen\n\n${results.joinToString("\n")}")
    }

    private fun renderEngine(engineName: String) {
        if (engineName == renderedEngine) return
        renderedEngine = engineName
        playerContainer.removeAllViews()
        val view = if (engineName == "Android MediaPlayer") engine.fallbackView else playerView
        playerContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun status(text: String) {
        statusView.text = text
        Log.d(TAG, text)
    }

    override fun onDestroy() {
        testJob?.cancel()
        engine.release()
        super.onDestroy()
    }

    private data class MediaCase(val label: String, val url: String)

    private companion object {
        const val TAG = "Frame61MediaTest"
        val CASES = listOf(
            MediaCase("HLS", "asset:///diagnostics/sample.m3u8"),
            MediaCase("MP4", "asset:///diagnostics/sample.mp4"),
            MediaCase("MKV", "asset:///diagnostics/sample.mkv"),
            MediaCase("AVI", "asset:///diagnostics/sample.avi"),
            MediaCase("WMV-Fallback", "asset:///diagnostics/sample.wmv"),
            MediaCase("MPEG-TS", "asset:///diagnostics/sample.ts")
        )
    }
}
