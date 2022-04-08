package com.ys.spotify.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ys.spotify.common.Constants.SONG_COLLECTION
import com.ys.spotify.data.entities.Song
import kotlinx.coroutines.tasks.await

class MusicDatabase {

    private val fireStore = FirebaseFirestore.getInstance()
    private val songCollection = fireStore.collection(SONG_COLLECTION)

    suspend fun getAllSongs(): List<Song> {
        return try {
            songCollection.get().await().toObjects(Song::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
