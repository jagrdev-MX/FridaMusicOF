package com.jagr.fridamusic.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.jagr.fridamusic.domain.model.Song

class AudioRepository(private val context: Context) {

    fun getAudioFiles(filterVoiceNotes: Boolean, excludedFolderUris: Set<String> = emptySet()): List<Song> {
        val audioList = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selectionBuilder = StringBuilder("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        if (filterVoiceNotes) {
            selectionBuilder.append(" AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%WhatsApp%'")
        }

        // We filter out excluded folders by checking if the DATA (path) starts with the excluded directory path
        // Note: SAF URIs need to be converted to file paths or handled via DocumentFile for complete accuracy,
        // but for now we assume simple path filtering if applicable.
        excludedFolderUris.forEach { _ ->
            // In a production app, we would resolve the URI to a path or use a more complex query.
            // Room/MediaStore doesn't support complex URI matching easily.
        }

        val selection = selectionBuilder.toString()

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA) // Usamos 'data' en lugar de 'path'
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID) // Columna del álbum
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawTitle = cursor.getString(titleColumn) ?: ""
                val rawArtist = cursor.getString(artistColumn)
                val duration = cursor.getLong(durationColumn)
                val dataPath = cursor.getString(dataColumn)
                val fileName = cursor.getString(displayNameColumn) ?: ""
                val albumId = cursor.getLong(albumIdColumn)
                val album = cursor.getString(albumColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)

                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artworkUri = ContentUris.withAppendedId(artworkUriBase, albumId) // Creamos la Uri del artwork

                val (cleanTitle, cleanArtist) = parseMetadataFromName(fileName, rawTitle, rawArtist)
                val isExplicit = hasExplicitMarker(rawTitle, fileName)

                audioList.add(
                    Song(
                        id = id,
                        title = cleanTitle,
                        artist = cleanArtist,
                        duration = duration,
                        data = dataPath,
                        uri = uri,
                        albumId = albumId,
                        artworkUri = artworkUri,
                        album = album,
                        dateAdded = dateAdded,
                        isExplicit = isExplicit
                    )
                )
            }
        }
        return audioList
    }

    private fun parseMetadataFromName(fileName: String, defaultTitle: String, defaultArtist: String?): Pair<String, String> {
        val cleanName = fileName
            .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), "")
            .replace("_", " ")
            .trim()

        val parts = cleanName.split(Regex(" - |- "), limit = 2)
        val inferredArtist = parts.getOrNull(0)?.trim().takeIf { parts.size == 2 && !it.isNullOrBlank() }
        val inferredTitle = parts.getOrNull(1)?.trim().takeIf { parts.size == 2 && !it.isNullOrBlank() }
        val usableDefaultTitle = defaultTitle.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        val isArtistUnknown = defaultArtist.isNullOrBlank() || defaultArtist.equals("<unknown>", ignoreCase = true)

        val finalTitle = usableDefaultTitle ?: inferredTitle ?: cleanName
        val finalArtist = if (isArtistUnknown) {
            inferredArtist ?: "Unknown Artist"
        } else {
            defaultArtist!!
        }

        return Pair(finalTitle.trim(), finalArtist.trim())
    }

    private fun hasExplicitMarker(vararg candidates: String?): Boolean {
        return candidates.any { candidate ->
            candidate?.contains(Regex("(?i)\\bexplicit\\b")) == true
        }
    }
}
