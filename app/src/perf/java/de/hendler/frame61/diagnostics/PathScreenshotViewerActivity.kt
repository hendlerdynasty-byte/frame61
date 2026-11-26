package de.hendler.frame61.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/**
 * Gerätelokaler Betrachter für die Path-Abnahme-Screenshots.
 *
 * Die Dateien bleiben im app-eigenen External-Files-Ordner. Der Betrachter
 * holt sie nicht aus dem Netz und macht keinen Upload; er dient nur dazu, die
 * auf dem Gerät erzeugten Belege per D-Pad zu öffnen und frisch zu prüfen.
 */
class PathScreenshotViewerActivity : ComponentActivity() {

    private lateinit var title: TextView
    private lateinit var image: ImageView
    private lateinit var previous: Button
    private lateinit var next: Button
    private val shots = mutableListOf<File>()
    private var index = 0
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = TextView(this).apply {
            textSize = 18f
            setPadding(24, 20, 24, 12)
            gravity = Gravity.CENTER
        }
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Path-Abnahme Screenshot"
        }
        previous = Button(this).apply {
            text = "Zurück"
            contentDescription = "Vorheriger Screenshot"
            setOnClickListener { move(-1) }
        }
        next = Button(this).apply {
            text = "Weiter"
            contentDescription = "Nächster Screenshot"
            setOnClickListener { move(1) }
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 24)
            addView(previous, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF060913.toInt())
            addView(title)
            addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(controls)
        }
        setContentView(root)

        getExternalFilesDir(PATH_DIR)?.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedWith(compareBy { it.name.substringBefore('-').toIntOrNull() ?: Int.MAX_VALUE })
            ?.let(shots::addAll)
        show()
    }

    private fun move(delta: Int) {
        if (shots.isEmpty()) return
        index = (index + delta).coerceIn(shots.indices)
        show()
    }

    private fun show() {
        if (shots.isEmpty()) {
            title.text = "Keine Path-Screenshots vorhanden"
            image.setImageDrawable(null)
            previous.isEnabled = false
            next.isEnabled = false
            return
        }
        val file = shots[index]
        title.text = "${index + 1} / ${shots.size} · ${file.name}"
        val sampled = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, sampled)
        val options = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(sampled.outWidth, sampled.outHeight) / 1600)
        }
        val loaded = BitmapFactory.decodeFile(file.absolutePath, options)
        bitmap?.recycle()
        bitmap = loaded
        image.setImageBitmap(loaded)
        previous.isEnabled = index > 0
        next.isEnabled = index < shots.lastIndex
    }

    override fun onDestroy() {
        bitmap?.recycle()
        bitmap = null
        super.onDestroy()
    }

    private companion object {
        const val PATH_DIR = "path-acceptance/screenshots"
    }
}
