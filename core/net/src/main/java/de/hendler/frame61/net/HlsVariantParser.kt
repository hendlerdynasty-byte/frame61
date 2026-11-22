package de.hendler.frame61.net

/** Resolution/bandwidth/HDR metadata from an HLS master playlist, never its URLs. */
data class HlsVariant(
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val codecs: String,
    val hdr: Boolean
)

object HlsVariantParser {
    fun parse(body: String): List<HlsVariant> {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
            val attributes = attributes(line.substringAfter(':'))
            val resolution = attributes["RESOLUTION"]?.lowercase()?.split('x')
            HlsVariant(
                bandwidth = attributes["BANDWIDTH"]?.toLongOrNull()
                    ?: attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L,
                width = resolution?.getOrNull(0)?.toIntOrNull() ?: 0,
                height = resolution?.getOrNull(1)?.toIntOrNull() ?: 0,
                codecs = attributes["CODECS"].orEmpty(),
                hdr = attributes["VIDEO-RANGE"]?.equals("SDR", ignoreCase = true) == false
            )
        }.sortedWith(compareByDescending<HlsVariant> { it.height }.thenByDescending { it.bandwidth })
    }

    fun summarize(body: String): Map<String, String> {
        val variants = parse(body)
        if (variants.isEmpty()) return mapOf("hlsVariants" to "0")
        val best = variants.first()
        return mapOf(
            "hlsVariants" to variants.size.toString(),
            "hlsMaxHeight" to best.height.toString(),
            "hlsMaxBandwidth" to best.bandwidth.toString(),
            "hlsHdr" to variants.any { it.hdr }.toString(),
            "hlsBestCodecs" to best.codecs
        )
    }

    private fun attributes(raw: String): Map<String, String> =
        ATTRIBUTE.findAll(raw).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
        }

    private val ATTRIBUTE = Regex("""([A-Z0-9-]+)=((?:"[^"]*")|[^,]*)""")
}
