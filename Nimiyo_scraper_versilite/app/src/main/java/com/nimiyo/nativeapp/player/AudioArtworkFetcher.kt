package com.nimiyo.nativeapp.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.buffer
import okio.source
import java.io.File

/**
 * Custom Coil Fetcher for `nimiyoart://track?path=...&uri=...&fallback=...`.
 * Reads each audio file's own embedded cover art via MediaMetadataRetriever FIRST,
 * instead of Android MediaStore's folder-shared `albumart/<albumId>` cache.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val filePath = uri.getQueryParameter("path").orEmpty()
        val contentUri = uri.getQueryParameter("uri").orEmpty()
        val fallback = uri.getQueryParameter("fallback").orEmpty()

        // 1. Per-file embedded picture (ID3 APIC / MP4 covr / FLAC picture)
        val embeddedBytes = extractEmbeddedPicture(filePath, contentUri)
        if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
            val buf = Buffer().write(embeddedBytes)
            return SourceResult(
                source = ImageSource(buf, options.context),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }

        // 2. Fallback to MediaStore albumArtUri only when file has no embedded art
        if (fallback.isNotBlank()) {
            val stream = runCatching {
                context.contentResolver.openInputStream(Uri.parse(fallback))
            }.getOrNull()
            if (stream != null) {
                return SourceResult(
                    source = ImageSource(stream.source().buffer(), options.context),
                    mimeType = null,
                    dataSource = DataSource.DISK,
                )
            }
        }
        return null
    }

    private fun extractEmbeddedPicture(filePath: String, contentUri: String): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            val ok = when {
                filePath.isNotBlank() && File(filePath).exists() -> {
                    mmr.setDataSource(filePath)
                    true
                }
                contentUri.isNotBlank() -> {
                    mmr.setDataSource(context, Uri.parse(contentUri))
                    true
                }
                else -> false
            }
            if (ok) mmr.embeddedPicture else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != SCHEME) return null
            return AudioArtworkFetcher(context.applicationContext, data, options)
        }
    }

    companion object {
        const val SCHEME = "nimiyoart"

        fun buildUri(filePath: String, contentUri: String, fallbackAlbumArt: String = ""): String {
            return Uri.Builder()
                .scheme(SCHEME)
                .authority("track")
                .appendQueryParameter("path", filePath)
                .appendQueryParameter("uri", contentUri)
                .appendQueryParameter("fallback", fallbackAlbumArt)
                .build()
                .toString()
        }
    }
}

