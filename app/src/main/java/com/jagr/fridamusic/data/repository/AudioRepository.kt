package com.jagr.fridamusic.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.jagr.fridamusic.domain.model.Song

class AudioRepository(private val context: Context) {

    fun getAudioFiles(filterVoiceNotes: Boolean): List<Song> {
        val audioList = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = if (filterVoiceNotes) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} NOT LIKE '%WhatsApp%'"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }

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

            val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val rawTitle = cursor.getString(titleColumn) ?: ""
                val rawArtist = cursor.getString(artistColumn)
                val duration = cursor.getLong(durationColumn)
                val dataPath = cursor.getString(dataColumn)
                val fileName = cursor.getString(displayNameColumn) ?: ""
                val albumId = cursor.getLong(albumIdColumn)

                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artworkUri = ContentUris.withAppendedId(artworkUriBase, albumId) // Creamos la Uri del artwork

                val (cleanTitle, cleanArtist) = parseMetadataFromName(fileName, rawTitle, rawArtist)

                audioList.add(
                    Song(
                        id = id,
                        title = cleanTitle,
                        artist = cleanArtist,
                        duration = duration,
                        data = dataPath,
                        uri = uri,
                        albumId = albumId,
                        artworkUri = artworkUri
                    )
                )
            }
        }
        return audioList
    }

    private fun parseMetadataFromName(fileName: String, defaultTitle: String, defaultArtist: String?): Pair<String, String> {
        var cleanName = fileName.replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), "")

        cleanName = cleanName.replace("_", " ")

        cleanName = cleanName.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        val parts = cleanName.split(Regex(" - |- "), limit = 2)

        return if (parts.size == 2) {
            val artist = parts[0].trim()
            val title = parts[1].trim()
            Pair(title, artist)
        } else {
            val isArtistUnknown = defaultArtist.isNullOrBlank() || defaultArtist.equals("<unknown>", ignoreCase = true)
            val finalArtist = if (isArtistUnknown) "Unknown Artist" else defaultArtist!!

            Pair(cleanName.trim(), finalArtist)
        }
    }
}