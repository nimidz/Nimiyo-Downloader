package com.nimiyo.nativeapp.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.nimiyo.nativeapp.core.OverwriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class MediaStoreRepository(private val context: Context) {

    private fun nimiyoRootDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "Nimiyo").apply { if (!exists()) mkdirs() }
    }

    fun categorySubDirName(category: MediaCategory): String = when (category) {
        MediaCategory.VIDEO -> "VideoYo"
        MediaCategory.AUDIO -> "AudioYo"
        MediaCategory.IMAGE -> "ImageYo"
    }

    fun categoryPublicDir(category: MediaCategory): File {
        return File(nimiyoRootDir(), categorySubDirName(category)).apply {
            if (!exists()) mkdirs()
        }
    }

    fun databaseDir(): File = File(nimiyoRootDir(), "Database").apply {
        if (!exists()) mkdirs()
        runCatching { File(this, ".nomedia").apply { if (!exists()) createNewFile() } }
    }

    fun lyricsDir(): File = File(nimiyoRootDir(), "LyricsYO").apply {
        if (!exists()) mkdirs()
        runCatching { File(this, ".nomedia").apply { if (!exists()) createNewFile() } }
    }

    fun sanitizeFilename(raw: String, maxLen: Int = 85): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "nimiyo_media_${System.currentTimeMillis()}" }
        return if (cleaned.length > maxLen) cleaned.take(maxLen).trim() else cleaned
    }

    private fun findExistingDownloadUri(subFolder: String, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(displayName, "%$subFolder%"),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    ContentUris.withAppendedId(collection, id)
                } else null
            }
        }.getOrNull()
    }

    private fun findChildDocumentInTree(treeUri: Uri, parentDocId: String, displayName: String): Uri? {
        return runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == displayName) {
                        return@runCatching DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun trySaveToSafTree(
        treeUriStr: String,
        safeBase: String,
        extClean: String,
        mimeType: String,
        totalBytes: Long,
        inputStream: InputStream,
        overwriteMode: OverwriteMode,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Pair<String, String>? {
        val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val resolver = context.contentResolver

        var resolvedName = "$safeBase.$extClean"
        val existingUri = findChildDocumentInTree(treeUri, treeDocId, resolvedName)
        if (existingUri != null) {
            when (overwriteMode) {
                OverwriteMode.SKIP -> {
                    runCatching { inputStream.close() }
                    return "" to existingUri.toString()
                }
                OverwriteMode.OVERWRITE -> {
                    runCatching { DocumentsContract.deleteDocument(resolver, existingUri) }
                }
                OverwriteMode.RENAME -> {
                    var idx = 1
                    while (idx < 500) {
                        val cand = "${safeBase}_$idx.$extClean"
                        if (findChildDocumentInTree(treeUri, treeDocId, cand) == null) {
                            resolvedName = cand
                            break
                        }
                        idx++
                    }
                }
            }
        }

        val createdUri = runCatching {
            DocumentsContract.createDocument(resolver, parentDocUri, mimeType, resolvedName)
        }.getOrNull() ?: return null

        return try {
            resolver.openOutputStream(createdUri)?.use { out ->
                copyWithProgress(inputStream, out, totalBytes, onProgress, isCancelled)
            } ?: throw IllegalStateException("Cannot open SAF output stream")
            "" to createdUri.toString()
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            throw e
        }
    }

    suspend fun saveStreamToDownloads(
        baseName: String,
        extension: String,
        category: MediaCategory,
        mimeType: String,
        totalBytes: Long,
        inputStream: InputStream,
        overwriteMode: OverwriteMode = OverwriteMode.RENAME,
        customTreeUri: String = "",
        customSubfolder: String? = null,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val safeBase = sanitizeFilename(baseName)
        val extClean = extension.lowercase().trimStart('.').ifBlank {
            when (category) {
                MediaCategory.VIDEO -> "mp4"
                MediaCategory.AUDIO -> "mp3"
                MediaCategory.IMAGE -> "jpg"
            }
        }

        if (customTreeUri.isNotBlank()) {
            val safResult = trySaveToSafTree(
                treeUriStr = customTreeUri,
                safeBase = safeBase,
                extClean = extClean,
                mimeType = mimeType,
                totalBytes = totalBytes,
                inputStream = inputStream,
                overwriteMode = overwriteMode,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )
            if (safResult != null) return@withContext safResult
        }

        val subLeaf = customSubfolder?.takeIf { it.isNotBlank() }?.let { sanitizeFilename(it, 48) }
            ?: categorySubDirName(category)
        val targetDir = File(nimiyoRootDir(), subLeaf).apply { if (!exists()) mkdirs() }
        val subFolder = "Nimiyo/$subLeaf"
        var resolvedName = "$safeBase.$extClean"
        val existing = File(targetDir, resolvedName)
        val existingMediaUri = findExistingDownloadUri(subFolder, resolvedName)
        if (existing.exists() || existingMediaUri != null) {
            when (overwriteMode) {
                OverwriteMode.SKIP -> {
                    runCatching { inputStream.close() }
                    val uriStr = existingMediaUri?.toString() ?: Uri.fromFile(existing).toString()
                    return@withContext existing.absolutePath to uriStr
                }
                OverwriteMode.OVERWRITE -> {
                    runCatching { if (existing.exists()) existing.delete() }
                    if (existingMediaUri != null) {
                        runCatching { context.contentResolver.delete(existingMediaUri, null, null) }
                    }
                }
                OverwriteMode.RENAME -> {
                    var idx = 1
                    while (idx < 500) {
                        val candidate = "${safeBase}_$idx.$extClean"
                        if (!File(targetDir, candidate).exists() && findExistingDownloadUri(subFolder, candidate) == null) {
                            resolvedName = candidate
                            break
                        }
                        idx++
                    }
                }
            }
        }
        val filename = resolvedName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Unable to create MediaStore download entry")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    copyWithProgress(inputStream, out, totalBytes, onProgress, isCancelled)
                } ?: throw IllegalStateException("Cannot open output stream for $uri")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)

                val actualName = resolver.query(
                    uri,
                    arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }?.takeIf { it.isNotBlank() } ?: filename
                val expectedFile = File(targetDir, actualName)
                return@withContext expectedFile.absolutePath to uri.toString()
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val outFile = File(targetDir, filename)
            try {
                outFile.outputStream().use { out ->
                    copyWithProgress(inputStream, out, totalBytes, onProgress, isCancelled)
                }
                return@withContext outFile.absolutePath to Uri.fromFile(outFile).toString()
            } catch (e: Exception) {
                runCatching { if (outFile.exists()) outFile.delete() }
                throw e
            }
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val buf = ByteArray(64 * 1024)
        var downloaded = 0L
        var lastReport = 0L
        while (true) {
            if (isCancelled()) throw InterruptedException("Cancelled by user")
            val read = input.read(buf)
            if (read == -1) break
            output.write(buf, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastReport >= 250) {
                onProgress(downloaded, totalBytes)
                lastReport = now
            }
        }
        output.flush()
        onProgress(downloaded, totalBytes)
    }

    // ---- Download History JSON ----

    private fun historyFile(): File = File(context.filesDir, "nimiyo_history.json")

    suspend fun loadHistory(): List<HistoryRecord> = withContext(Dispatchers.IO) {
        val file = historyFile()
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<HistoryRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    HistoryRecord(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        filename = obj.optString("filename"),
                        category = MediaCategory.entries.firstOrNull { it.name == obj.optString("category") }
                            ?: MediaCategory.VIDEO,
                        platform = obj.optString("platform", "web"),
                        localPath = obj.optString("localPath"),
                        contentUri = obj.optString("contentUri"),
                        thumbnail = obj.optString("thumbnail"),
                        sourceUrl = obj.optString("sourceUrl"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            list.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    suspend fun saveHistory(records: List<HistoryRecord>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        records.forEach { rec ->
            arr.put(JSONObject().apply {
                put("id", rec.id)
                put("title", rec.title)
                put("filename", rec.filename)
                put("category", rec.category.name)
                put("platform", rec.platform)
                put("localPath", rec.localPath)
                put("contentUri", rec.contentUri)
                put("thumbnail", rec.thumbnail)
                put("sourceUrl", rec.sourceUrl)
                put("sizeBytes", rec.sizeBytes)
                put("createdAt", rec.createdAt)
            })
        }
        historyFile().writeText(arr.toString())
    }

    // ---- Audio Library Scanner ----

    suspend fun scanAudioLibrary(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AudioTrack>()
        val seenPaths = HashSet<String>()

        val nimiyoAudioDir = categoryPublicDir(MediaCategory.AUDIO)
        val audioExts = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
        val lrcNames = HashSet<String>().apply {
            runCatching {
                lyricsDir().listFiles()?.forEach { f ->
                    if (f.isFile && f.extension.equals("lrc", ignoreCase = true)) {
                        add(f.nameWithoutExtension.lowercase())
                    }
                }
            }
        }
        fun trackHasLrc(filePath: String, title: String): Boolean {
            if (filePath.isNotBlank()) {
                val f = File(filePath)
                if (File(f.parentFile, "${f.nameWithoutExtension}.lrc").exists()) return true
                if (lrcNames.contains(f.nameWithoutExtension.lowercase())) return true
            }
            return lrcNames.contains(sanitizeFilename(title).lowercase())
        }

        // 1. Direct scan of Downloads/Nimiyo/AudioYo
        nimiyoAudioDir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase() in audioExts) {
                seenPaths.add(f.absolutePath)
                val fileUri = Uri.fromFile(f).toString()
                result.add(
                    AudioTrack(
                        id = "file:${f.absolutePath}",
                        title = f.nameWithoutExtension,
                        artist = "Nimiyo Offline",
                        album = "AudioYo",
                        path = f.absolutePath,
                        contentUri = fileUri,
                        durationMs = 0L,
                        sizeBytes = f.length(),
                        dateAddedSec = f.lastModified() / 1000L,
                        isNimiyoFolder = true,
                        albumArtUri = com.nimiyo.nativeapp.player.AudioArtworkFetcher.buildUri(f.absolutePath, fileUri),
                        hasLrc = trackHasLrc(f.absolutePath, f.nameWithoutExtension),
                    )
                )
            }
        }

        // 2. MediaStore.Audio scan
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE ?",
                arrayOf("%Nimiyo/AudioYo%"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val dur = cursor.getLong(durCol)
                    if (dur in 1..4000) continue // ignore short notification tones
                    val isNimiyo = path.contains("Nimiyo/AudioYo", ignoreCase = true) ||
                        path.contains("Nimiyo\\AudioYo", ignoreCase = true)
                    val title = cursor.getString(titleCol).orEmpty().ifBlank { File(path).nameWithoutExtension }
                    val artist = cursor.getString(artistCol).orEmpty()
                        .let { if (it == "<unknown>") "Unknown Artist" else it }
                        .ifBlank { "Unknown Artist" }
                    val album = cursor.getString(albumCol).orEmpty().ifBlank { "Unknown Album" }
                    val albumId = cursor.getLong(albumIdCol)
                    val msArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                    } else ""
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val artUri = com.nimiyo.nativeapp.player.AudioArtworkFetcher.buildUri(path, uri, msArtUri)

                    // Update metadata if already found via direct file scan
                    val existingIdx = result.indexOfFirst { it.path == path }
                    val track = AudioTrack(
                        id = "ms:$id",
                        title = title,
                        artist = artist,
                        album = album,
                        path = path,
                        contentUri = uri,
                        durationMs = dur,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        isNimiyoFolder = isNimiyo,
                        albumArtUri = artUri,
                        hasLrc = trackHasLrc(path, title),
                    )
                    if (existingIdx >= 0) {
                        result[existingIdx] = track
                    } else if (path.isBlank() || seenPaths.add(path)) {
                        result.add(track)
                    }
                }
            }
        }
        result.sortedByDescending { it.dateAddedSec }
    }

    // ---- Playlists in Downloads/Nimiyo/Database & Downloads/Nimiyo/database (Native + Nimiyo Lite + ID-only compatible) ----

    private fun playlistsFile(): File {
        val dbDir = runCatching { databaseDir() }.getOrElse { context.filesDir }
        return File(dbDir, "nimiyo_playlists.json")
    }

    private fun internalPlaylistsFallback(): File = File(context.filesDir, "nimiyo_playlists.json")

    private fun playlistPrefs() = context.getSharedPreferences("nimiyo_playlist_sync", Context.MODE_PRIVATE)

    fun markPlaylistDeleted(playlistId: String) {
        val key = playlistId.trim().lowercase()
        if (key.isBlank()) return
        val prefs = playlistPrefs()
        val set = (prefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()).toMutableSet()
        set.add(key)
        prefs.edit().putStringSet("deleted_ids", set).apply()
    }

    suspend fun deleteAudioTrack(track: AudioTrack): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        if (track.contentUri.startsWith("content://", ignoreCase = true)) {
            deleted = runCatching {
                context.contentResolver.delete(Uri.parse(track.contentUri), null, null) > 0
            }.getOrDefault(false)
        }
        if (!deleted && track.path.isNotBlank()) {
            val f = File(track.path)
            if (f.exists() && f.delete()) {
                deleted = true
                runCatching { MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null) }
            }
        }
        if (deleted && track.path.isNotBlank()) {
            val base = File(track.path).nameWithoutExtension
            val parent = File(track.path).parentFile
            listOf("lrc", "ttml").forEach { ext ->
                parent?.let { runCatching { File(it, "$base.$ext").delete() } }
                runCatching { File(lyricsDir(), "$base.$ext").delete() }
            }
        }
        deleted
    }

    suspend fun moveAudioTrackToNimiyo(track: AudioTrack): Boolean = withContext(Dispatchers.IO) {
        val targetDir = categoryPublicDir(MediaCategory.AUDIO)
        val src = track.path.takeIf { it.isNotBlank() }?.let { File(it) }
        if (src != null && src.exists()) {
            val sameDir = runCatching { src.parentFile?.canonicalPath == targetDir.canonicalPath }.getOrDefault(false)
            if (sameDir) return@withContext false
            val dest = File(targetDir, src.name)
            val copiedDirect = runCatching {
                src.copyTo(dest, overwrite = true)
                true
            }.getOrDefault(false)
            if (copiedDirect) {
                var removedSrc = runCatching { src.delete() }.getOrDefault(false)
                if (!removedSrc && track.contentUri.startsWith("content://", ignoreCase = true)) {
                    removedSrc = runCatching {
                        context.contentResolver.delete(Uri.parse(track.contentUri), null, null) > 0
                    }.getOrDefault(false)
                }
                val base = src.nameWithoutExtension
                src.parentFile?.let { oldDir ->
                    listOf("lrc", "ttml").forEach { ext ->
                        val sidecar = File(oldDir, "$base.$ext")
                        if (sidecar.exists()) {
                            runCatching {
                                sidecar.copyTo(File(lyricsDir(), "$base.$ext"), overwrite = true)
                                sidecar.delete()
                            }
                        }
                    }
                }
                runCatching { MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath, src.absolutePath), null, null) }
                return@withContext removedSrc || dest.exists()
            }
        }
        if (track.contentUri.startsWith("content://", ignoreCase = true)) {
            val srcUri = Uri.parse(track.contentUri)
            val mime = context.contentResolver.getType(srcUri) ?: "audio/mpeg"
            val baseName = track.title.ifBlank { "track_${System.currentTimeMillis()}" }
            val ext = mime.substringAfter('/', "mp3").substringBefore(';')
            val copied = runCatching {
                context.contentResolver.openInputStream(srcUri)?.use { inp ->
                    saveStreamToDownloads(
                        baseName = baseName,
                        extension = ext,
                        category = MediaCategory.AUDIO,
                        mimeType = mime,
                        totalBytes = -1L,
                        inputStream = inp,
                        overwriteMode = OverwriteMode.RENAME,
                        onProgress = { _, _ -> },
                        isCancelled = { false },
                    )
                }
                true
            }.getOrDefault(false)
            if (copied) {
                runCatching { context.contentResolver.delete(srcUri, null, null) }
                return@withContext true
            }
        }
        false
    }

    private fun extractStringField(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val v = obj.opt(k)?.toString()?.trim().orEmpty()
            if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) return v
        }
        return ""
    }

    private fun parsePlaylistObject(obj: JSONObject, fallbackIdx: Int, mapKey: String? = null): PlaylistModel? {
        val rawId = extractStringField(obj, "id", "playlistId", "playlist_id", "plId", "pl_id")
            .ifBlank { mapKey?.trim().orEmpty() }
        val rawName = extractStringField(obj, "name", "title", "playlistName", "playlist_name", "label")
        if (rawId.isBlank() && rawName.isBlank()) return null

        val id = rawId.ifBlank { "pl_${rawName.lowercase().hashCode()}_$fallbackIdx" }
        val name = rawName.ifBlank { id }
        val keys = LinkedHashSet<String>()

        fun collectArray(arr: JSONArray?) {
            if (arr == null) return
            for (j in 0 until arr.length()) {
                when (val item = arr.opt(j)) {
                    is String -> if (item.isNotBlank()) keys.add(item.trim())
                    is Number -> keys.add(item.toString())
                    is JSONObject -> {
                        val candidate = extractStringField(
                            item,
                            "path",
                            "filePath",
                            "file_path",
                            "uri",
                            "contentUri",
                            "content_uri",
                            "fileId",
                            "file_id",
                            "trackId",
                            "track_id",
                            "mediaId",
                            "media_id",
                            "audioId",
                            "audio_id",
                            "id",
                            "fileName",
                            "filename",
                            "name",
                        )
                        if (candidate.isNotBlank()) keys.add(candidate)
                    }
                }
            }
        }

        val idFields = listOf(
            "trackIds",
            "track_ids",
            "fileIds",
            "file_ids",
            "ids",
        )
        for (field in idFields) {
            collectArray(obj.optJSONArray(field))
        }

        // Only fall back to legacy path/name arrays when no explicit ID array was present
        if (keys.isEmpty()) {
            val fallbackFields = listOf(
                "tracks",
                "trackPaths",
                "track_paths",
                "songs",
                "files",
                "items",
                "trackFileNames",
            )
            for (field in fallbackFields) {
                collectArray(obj.optJSONArray(field))
            }
        }

        val updatedAt = obj.optLong("updatedAt", obj.optLong("createdAt", System.currentTimeMillis()))
        return PlaylistModel(
            id = id,
            name = name,
            trackPaths = keys.toList(),
            updatedAt = updatedAt,
        )
    }

    private fun parsePlaylistRaw(rawInput: String, outMap: MutableMap<String, PlaylistModel>) {
        runCatching {
            val raw = rawInput.trim()
            if (raw.isEmpty()) return
            val parsed = mutableListOf<PlaylistModel>()
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { parsePlaylistObject(it, i) }?.let { parsed.add(it) }
                }
            } else if (raw.startsWith("{")) {
                val root = JSONObject(raw)
                val nestedArr = root.optJSONArray("playlists")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("database")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("lists")
                if (nestedArr != null) {
                    for (i in 0 until nestedArr.length()) {
                        nestedArr.optJSONObject(i)?.let { parsePlaylistObject(it, i) }?.let { parsed.add(it) }
                    }
                } else {
                    val single = parsePlaylistObject(root, outMap.size)
                    if (single != null) {
                        parsed.add(single)
                    } else {
                        // Map-style object: { "playlist_id_1": { ... }, "playlist_id_2": { ... } }
                        val keysIter = root.keys()
                        var idx = 0
                        while (keysIter.hasNext()) {
                            val k = keysIter.next()
                            root.optJSONObject(k)?.let { parsePlaylistObject(it, idx++, mapKey = k) }?.let { parsed.add(it) }
                        }
                    }
                }
            }
            parsed.forEach { pl ->
                val byIdKey = pl.id.trim().lowercase()
                val byNameKey = pl.name.trim().lowercase()
                val existingKey = outMap.entries.firstOrNull { (_, ex) ->
                    ex.id.trim().lowercase() == byIdKey ||
                        (byNameKey.isNotBlank() && ex.name.trim().lowercase() == byNameKey)
                }?.key
                if (existingKey == null) {
                    outMap[byIdKey.ifBlank { byNameKey }] = pl
                } else {
                    val ex = outMap.getValue(existingKey)
                    val mergedTracks = (ex.trackPaths + pl.trackPaths).distinct()
                    val preferredName = if (ex.name != ex.id) ex.name else pl.name
                    outMap[existingKey] = ex.copy(
                        id = if (pl.id.isNotBlank()) pl.id else ex.id,
                        name = preferredName,
                        trackPaths = mergedTracks,
                        updatedAt = maxOf(ex.updatedAt, pl.updatedAt),
                    )
                }
            }
        }
    }

    private fun parsePlaylistFile(file: File, outMap: MutableMap<String, PlaylistModel>) {
        runCatching { parsePlaylistRaw(file.readText(), outMap) }
    }

    suspend fun loadPlaylists(): List<PlaylistModel> = withContext(Dispatchers.IO) {
        val merged = linkedMapOf<String, PlaylistModel>()
        val candidateDirs = listOfNotNull(
            runCatching { databaseDir() }.getOrNull(),
            runCatching { File(nimiyoRootDir(), "database") }.getOrNull(),
            runCatching { File(nimiyoRootDir(), "Database") }.getOrNull(),
        ).distinctBy { dir -> runCatching { dir.canonicalPath.lowercase() }.getOrElse { dir.absolutePath.lowercase() } }

        val seenFiles = hashSetOf<String>()
        candidateDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { f ->
                    f.isFile &&
                        f.extension.equals("json", ignoreCase = true) &&
                        !f.name.contains("history", ignoreCase = true) &&
                        !f.name.contains("settings", ignoreCase = true)
                }?.sortedBy { it.name }?.forEach { f ->
                    val key = runCatching { f.canonicalPath.lowercase() }.getOrElse { f.absolutePath.lowercase() }
                    if (seenFiles.add(key)) {
                        parsePlaylistFile(f, merged)
                    }
                }
            }
        }

        // MediaStore.Downloads fallback for Android 10+ (reads legacy Nimiyo/Database/*.json across reinstalls)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.RELATIVE_PATH,
                )
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? OR ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf("%Nimiyo/Database%", "%Nimiyo/database%"),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol).orEmpty()
                        if (!name.endsWith(".json", ignoreCase = true)) continue
                        if (name.contains("history", ignoreCase = true) || name.contains("settings", ignoreCase = true)) continue
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()?.let { raw ->
                            parsePlaylistRaw(raw, merged)
                        }
                    }
                }
            }
        }

        val fallback = internalPlaylistsFallback()
        if (fallback.exists()) {
            parsePlaylistFile(fallback, merged)
        }

        val deletedIds = playlistPrefs().getStringSet("deleted_ids", emptySet()) ?: emptySet()
        merged.values
            .filterNot { it.id.trim().lowercase() in deletedIds }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun savePlaylists(playlists: List<PlaylistModel>) = withContext(Dispatchers.IO) {
        val prefs = playlistPrefs()
        val deletedIds = (prefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()).toMutableSet()
        playlists.forEach { deletedIds.remove(it.id.trim().lowercase()) }
        prefs.edit().putStringSet("deleted_ids", deletedIds).apply()

        val arr = JSONArray()
        playlists.forEach { pl ->
            val trackIds = pl.trackPaths.mapNotNull { raw ->
                val key = raw.trim()
                if (key.isEmpty()) return@mapNotNull null
                when {
                    key.startsWith("/") -> {
                        val h = key.hashCode()
                        "file_${if (h == Int.MIN_VALUE) 0 else kotlin.math.abs(h)}"
                    }
                    key.startsWith("file:/") -> {
                        val p = key.removePrefix("file:")
                        val h = p.hashCode()
                        "file_${if (h == Int.MIN_VALUE) 0 else kotlin.math.abs(h)}"
                    }
                    else -> key
                }
            }.distinct()
            arr.put(JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("trackIds", JSONArray(trackIds))
                put("updatedAt", pl.updatedAt)
            })
        }
        val payload = arr.toString(2)
        runCatching { playlistsFile().writeText(payload) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val relPath = "${Environment.DIRECTORY_DOWNLOADS}/Nimiyo/Database"
                val fileName = "nimiyo_playlists_db.json"
                val existingUri = findExistingDownloadUri("Nimiyo/Database", fileName)
                if (existingUri != null) {
                    val wrote = runCatching {
                        context.contentResolver.openOutputStream(existingUri, "wt")?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                    if (!wrote) {
                        runCatching { context.contentResolver.delete(existingUri, null, null) }
                        context.contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                            },
                        )?.let { freshUri ->
                            context.contentResolver.openOutputStream(freshUri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                } else {
                    context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/json")
                            put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                        },
                    )?.let { freshUri ->
                        context.contentResolver.openOutputStream(freshUri)?.use { out ->
                            out.write(payload.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
        runCatching { internalPlaylistsFallback().writeText(payload) }
    }

    data class NimiyoFolderSizes(
        val videoBytes: Long = 0L,
        val audioBytes: Long = 0L,
        val imageBytes: Long = 0L,
        val databaseBytes: Long = 0L,
        val lyricsBytes: Long = 0L,
    ) {
        val totalMediaBytes: Long get() = videoBytes + audioBytes + imageBytes
    }

    suspend fun computeFolderSizes(): NimiyoFolderSizes = withContext(Dispatchers.IO) {
        fun dirSize(dir: File): Long =
            runCatching { dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() } }.getOrDefault(0L)
        NimiyoFolderSizes(
            videoBytes = dirSize(categoryPublicDir(MediaCategory.VIDEO)),
            audioBytes = dirSize(categoryPublicDir(MediaCategory.AUDIO)),
            imageBytes = dirSize(categoryPublicDir(MediaCategory.IMAGE)),
            databaseBytes = dirSize(databaseDir()),
            lyricsBytes = dirSize(lyricsDir()),
        )
    }

    suspend fun computeTotalMediaSizeBytes(): Long = withContext(Dispatchers.IO) {
        computeFolderSizes().totalMediaBytes
    }

    suspend fun clearAppCache(): Long = withContext(Dispatchers.IO) {
        var cleared = 0L
        runCatching {
            context.cacheDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val len = f.length()
                if (f.delete()) cleared += len
            }
        }
        cleared
    }
}

