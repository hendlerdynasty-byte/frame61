package de.hendler.frame61.diagnostics

import android.graphics.Color
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.data.ResumeRepository
import de.hendler.frame61.player.Media3Frame61Player
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Perf-only 30-minute session, resume and profile stress proof. */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class SessionStressActivity : ComponentActivity() {
    @Inject lateinit var resumeRepository: ResumeRepository
    @Inject lateinit var prefs: ProfileDataStore

    private lateinit var engine: Media3Frame61Player
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: FrameLayout
    private lateinit var statusView: TextView
    private var stressJob: Job? = null
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
            textSize = 15f
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
        de.hendler.frame61.net.PlaybackDiagnostics.beginSession("session-stress", 0)
        engine.attach()
        engine.exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        stressJob = lifecycleScope.launch { runStress() }
    }

    private suspend fun runStress() {
        val started = SystemClock.elapsedRealtime()
        var titles = 0
        var resumeJumps = 0
        var profileChanges = 0
        var errors = 0
        var maxHeap = Debug.getNativeHeapAllocatedSize()
        var profileId = 0L
        while (titles < 50) {
            val titleNumber = titles + 1
            if (titleNumber % 5 == 1) {
                profileId = (profileChanges % 2).toLong()
                prefs.setCurrentProfileId(profileId)
                profileChanges++
                Log.i(TAG, "Profilwechsel $profileChanges auf $profileId")
            }
            val titleId = 100_000L + titleNumber
            val mediaCase = CASES[(titleNumber - 1) % CASES.size]
            val previous = resumeRepository.load(profileId, titleId, 0L)
            engine.stop()
            engine.open(mediaCase.url, startAtMs = previous?.positionMs ?: 0L)
            titles++
            var hadError = false
            for (second in 1..36) {
                delay(1_000)
                val state = engine.state.value
                renderEngine(state.engine)
                statusView.text = "Titel $titleNumber/50 · $mediaCase | ${state.engine}\n" +
                    "Resume $resumeJumps/20 · Profile $profileChanges/10\n" +
                    "${state.positionMs}/${state.durationMs} ms · ${state.error ?: "kein Fehler"}"
                hadError = hadError || state.error != null
                if (second == 5 && titleNumber % 5 in setOf(2, 4)) {
                    val expected = if (state.positionMs < 1_500) 1_500L else 1_000L
                    engine.seekTo(expected)
                    resumeRepository.save(profileId, titleId, 0L, expected, state.durationMs)
                    val point = resumeRepository.load(profileId, titleId, 0L)
                    checkNotNull(point) { "Resume-Punkt fehlt nach Save" }
                    engine.open(mediaCase.url, startAtMs = point.positionMs)
                    resumeJumps++
                    Log.i(
                        TAG,
                        "Resume-Sprung $resumeJumps profil=$profileId title=$titleId " +
                            "erwartet=$expected gespeichert=${point.positionMs}"
                    )
                }
            }
            val state = engine.state.value
            resumeRepository.save(profileId, titleId, 0L, state.positionMs, state.durationMs)
            if (hadError) errors++
            if (titleNumber % 10 == 0) {
                System.gc()
                delay(500)
            }
            maxHeap = maxOf(maxHeap, Debug.getNativeHeapAllocatedSize())
            Log.i(
                TAG,
                "Titel $titleNumber/50 abgeschlossen engine=${state.engine} " +
                    "position=${state.positionMs} duration=${state.durationMs} " +
                    "fehler=$hadError heap=${Debug.getNativeHeapAllocatedSize()} maxHeap=$maxHeap"
            )
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        val pass = titles == 50 && resumeJumps == 20 && profileChanges == 10 && errors == 0
        val summary = "SESSION ${if (pass) "PASS" else "FAIL"} titles=$titles " +
            "resumeJumps=$resumeJumps profileChanges=$profileChanges errors=$errors " +
            "elapsedMs=$elapsed maxHeap=$maxHeap"
        Log.i(TAG, summary)
        statusView.text = summary
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

    override fun onDestroy() {
        stressJob?.cancel()
        engine.release()
        super.onDestroy()
    }

    private data class MediaCase(val label: String, val url: String)

    private companion object {
        const val TAG = "Frame61Session"
        val CASES = listOf(
            MediaCase("HLS", "asset:///diagnostics/sample.m3u8"),
            MediaCase("MP4", "asset:///diagnostics/sample.mp4"),
            MediaCase("MKV", "asset:///diagnostics/sample.mkv"),
            MediaCase("AVI", "asset:///diagnostics/sample.avi"),
            MediaCase("MPEG-TS", "asset:///diagnostics/sample.ts")
        )
    }
}
