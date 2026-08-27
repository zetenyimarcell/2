package com.hangfolyam.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hangfolyam.app.LiveSong
import kotlinx.coroutines.tasks.await

class CollectionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun ref() = auth.currentUser?.uid?.let {
        db.collection("users").document(it).collection("saved_songs")
    }

    suspend fun getAll(): List<LiveSong> {
        val snapshot = ref()?.get()?.await() ?: return emptyList()
        return snapshot.documents.mapNotNull { doc ->
            LiveSong(
                id = doc.id,
                title = doc.getString("title") ?: return@mapNotNull null,
                artist = doc.getString("artist") ?: "",
                coverUrl = doc.getString("coverUrl") ?: "",
                streamUrl = doc.getString("streamUrl") ?: ""
            )
        }
    }

    suspend fun add(song: LiveSong) {
        ref()?.document(song.id)?.set(
            mapOf("title" to song.title, "artist" to song.artist, "coverUrl" to song.coverUrl, "streamUrl" to song.streamUrl)
        )?.await()
    }

    suspend fun remove(songId: String) {
        ref()?.document(songId)?.delete()?.await()
    }
}
