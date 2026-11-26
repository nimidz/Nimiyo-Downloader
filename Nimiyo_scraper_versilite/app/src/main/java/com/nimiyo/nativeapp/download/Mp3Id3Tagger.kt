package com.nimiyo.nativeapp.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Writes an ID3v2.3 header (TIT2 title, TPE1 artist, TALB album, APIC cover art)
 * to MP3 files that lack complete artist/artwork tags (e.g. YouTube-matched MP3s).
 */
object Mp3Id3Tagger {

    fun tagFileInPlaceIfNeeded(
        mp3File: File,
        title: String,
        artist: String,
        album: String = "",
        coverBytes: ByteArray? = null,
        coverMime: String = "image/jpeg",
    ) {
        if (!mp3File.exists() || mp3File.length() < 128) return
        if (title.isBlank() && artist.isBlank() && (coverBytes == null || coverBytes.isEmpty())) return

        val headerBytes = ByteArray(10)
        mp3File.inputStream().use { inp ->
            if (inp.read(headerBytes) != 10) return
        }

        var audioOffset = 0L
        if (headerBytes[0] == 'I'.code.toByte() &&
            headerBytes[1] == 'D'.code.toByte() &&
            headerBytes[2] == '3'.code.toByte()
        ) {
            val tagSize = ((headerBytes[6].toInt() and 0x7F) shl 21) or
                ((headerBytes[7].toInt() and 0x7F) shl 14) or
                ((headerBytes[8].toInt() and 0x7F) shl 7) or
                (headerBytes[9].toInt() and 0x7F)
            val hasFooter = (headerBytes[5].toInt() and 0x10) != 0
            val totalTagBytes = 10L + tagSize.toLong() + if (hasFooter) 10L else 0L

            if (totalTagBytes < mp3File.length()) {
                val inspectLen = totalTagBytes.coerceAtMost(256 * 1024L).toInt()
                val existingTag = ByteArray(inspectLen)
                mp3File.inputStream().use { inp ->
                    inp.read(existingTag)
                }
                val tagAscii = String(existingTag, StandardCharsets.ISO_8859_1)
                val hasArtist = tagAscii.contains("TPE1")
                val hasCover = tagAscii.contains("APIC")
                if (hasArtist && (coverBytes == null || coverBytes.isEmpty() || hasCover)) {
                    return
                }
                audioOffset = totalTagBytes
            }
        }

        val cleanTitle = stripArtistPrefix(title, artist).ifBlank { title }
        val framesOut = ByteArrayOutputStream()
        if (cleanTitle.isNotBlank()) {
            framesOut.write(buildTextFrame("TIT2", cleanTitle))
        }
        if (artist.isNotBlank()) {
            framesOut.write(buildTextFrame("TPE1", artist))
        }
        if (album.isNotBlank()) {
            framesOut.write(buildTextFrame("TALB", album))
        }
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            framesOut.write(buildApicFrame(coverBytes, coverMime))
        }
        val framesBytes = framesOut.toByteArray()
        if (framesBytes.isEmpty()) return

        val tempTagged = File(mp3File.parentFile, "${mp3File.name}.tagged")
        try {
            FileOutputStream(tempTagged).use { out ->
                // ID3v2.3 header (10 bytes)
                out.write('I'.code)
                out.write('D'.code)
                out.write('3'.code)
                out.write(3) // major version 2.3
                out.write(0) // revision 0
                out.write(0) // flags
                out.write(encodeSynchsafe(framesBytes.size))
                out.write(framesBytes)

                mp3File.inputStream().use { inp ->
                    if (audioOffset > 0L) {
                        var toSkip = audioOffset
                        while (toSkip > 0L) {
                            val skipped = inp.skip(toSkip)
                            if (skipped <= 0L) break
                            toSkip -= skipped
                        }
                    }
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                    }
                }
                out.flush()
            }
            if (mp3File.delete()) {
                tempTagged.renameTo(mp3File)
            } else {
                tempTagged.copyTo(mp3File, overwrite = true)
                tempTagged.delete()
            }
        } catch (_: Exception) {
            tempTagged.delete()
        }
    }

    private fun stripArtistPrefix(title: String, artist: String): String {
        if (artist.isBlank()) return title.trim()
        val prefix = "$artist - "
        return if (title.startsWith(prefix, ignoreCase = true)) {
            title.substring(prefix.length).trim()
        } else {
            title.trim()
        }
    }

    private fun buildTextFrame(frameId: String, value: String): ByteArray {
        val textBytes = value.toByteArray(StandardCharsets.UTF_16LE)
        val payloadSize = 1 + 2 + textBytes.size // encoding (0x01) + BOM (FF FE) + UTF-16LE
        val out = ByteArrayOutputStream(10 + payloadSize)
        out.write(frameId.toByteArray(StandardCharsets.US_ASCII), 0, 4)
        out.write(encodeIntBigEndian(payloadSize))
        out.write(0)
        out.write(0)
        out.write(0x01) // UTF-16 with BOM
        out.write(0xFF)
        out.write(0xFE)
        out.write(textBytes)
        return out.toByteArray()
    }

    private fun buildApicFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(StandardCharsets.ISO_8859_1)
        val payloadSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size
        val out = ByteArrayOutputStream(10 + payloadSize)
        out.write("APIC".toByteArray(StandardCharsets.US_ASCII), 0, 4)
        out.write(encodeIntBigEndian(payloadSize))
        out.write(0)
        out.write(0)
        out.write(0x00) // ISO-8859-1 encoding for mime & description
        out.write(mimeBytes)
        out.write(0x00) // null terminator for mime
        out.write(0x03) // picture type: Front Cover
        out.write(0x00) // empty description null terminator
        out.write(imageBytes)
        return out.toByteArray()
    }

    private fun encodeSynchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun encodeIntBigEndian(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )
}

