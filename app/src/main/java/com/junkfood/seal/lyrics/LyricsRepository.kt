package com.junkfood.seal.lyrics

import com.junkfood.seal.util.VideoInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Small, cache-free LRCLIB client. Call it only after the user enables lyric download. */
class LyricsRepository(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun findTimedLyrics(info: VideoInfo): TimedLyrics? = withContext(Dispatchers.IO) {
        val query = SongQuery(title = info.title, artist = info.uploader ?: info.channel.orEmpty())
        if (query.title.isBlank() || query.artist.isBlank()) return@withContext null
        val url = "https://lrclib.net/api/get?track_name=${query.title.encode()}&artist_name=${query.artist.encode()}"
        val request = Request.Builder().url(url).header("User-Agent", "Seal-Lyrics/1.0").build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = Json { ignoreUnknownKeys = true }.decodeFromString<LrcLibResponse>(response.body.string())
                body.syncedLyrics?.takeIf { it.isNotBlank() }?.let { TimedLyrics(query, it, LrcParser.parse(it)) }
            }
        }.getOrNull()
    }

    private fun String.encode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

data class SongQuery(val title: String, val artist: String)
data class TimedLyrics(val query: SongQuery, val rawLrc: String, val lines: List<LrcLine>)
data class LrcLine(val timeMs: Long, val text: String)

@Serializable private data class LrcLibResponse(val syncedLyrics: String? = null)

object LrcParser {
    private val stamp = Regex("""\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?]""")
    fun parse(lrc: String): List<LrcLine> = lrc.lineSequence().flatMap { line ->
        val text = line.replace(stamp, "").trim()
        stamp.findAll(line).map { match ->
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            LrcLine((minutes * 60 + seconds) * 1_000 + fraction, text)
        }
    }.toList()
}
