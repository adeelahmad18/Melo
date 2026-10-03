package com.junkfood.seal.lyrics

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Writes a standards-based ID3v2 USLT (unsynchronised lyrics) frame for MP3 files.
 * M4A must be remuxed through the bundled FFmpeg metadata post-processor; exposing that as a
 * command keeps the download pipeline atomic and avoids corrupting MP4 atom offsets.
 */
object LyricsMetadataPatcher {
    fun patchMp3(file: File, lyrics: TimedLyrics) {
        require(file.extension.equals("mp3", ignoreCase = true))
        val body = buildUsltFrame(lyrics.rawLrc)
        val tag = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + synchsafe(body.size) + body
        val original = file.readBytes()
        file.writeBytes(tag + original)
    }

    /** Arguments for FFmpeg's metadata post-processor. `lyrics` maps to the M4A ©lyr atom. */
    fun m4aRemuxArguments(input: File, output: File, lyrics: TimedLyrics): List<String> = listOf(
        "-i", input.absolutePath, "-map", "0", "-c", "copy", "-metadata", "lyrics=${lyrics.rawLrc}", output.absolutePath,
    )

    private fun buildUsltFrame(lrc: String): ByteArray {
        val payload = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) + lrc.toByteArray(StandardCharsets.UTF_8)
        return "USLT".toByteArray(StandardCharsets.ISO_8859_1) + int32(payload.size) + byteArrayOf(0, 0) + payload
    }

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7f).toByte(), ((value shr 14) and 0x7f).toByte(), ((value shr 7) and 0x7f).toByte(), (value and 0x7f).toByte(),
    )

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte(),
    )
}
