package com.hangfolyam.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Valódi, ténylegesen lejátszott felvételek felismerése (mint a Shazam).
 * KORLÁT: dúdolást/éneklést NEM ismer fel megbízhatóan — csak stúdiófelvételt.
 * Ingyenes token: https://dashboard.audd.io/
 */
object RecognitionApi {
    private val client = OkHttpClient()
    private const val API_TOKEN = "3c3ef271303bbfad486351e6b66e49dd"

    suspend fun recognize(audioFile: File): RecognitionResult? = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_token", API_TOKEN)
            .addFormDataPart("return", "spotify")
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url("https://api.audd.io/").post(requestBody).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (json.optString("status") != "success") return@withContext null
            val result = json.optJSONObject("result") ?: return@withContext null
            RecognitionResult(
                title = result.optString("title"),
                artist = result.optString("artist"),
                album = result.optString("album"),
                spotifyUrl = result.optJSONObject("spotify")
                    ?.optJSONObject("external_urls")
                    ?.optString("spotify")
            )
        }
    }
}

data class RecognitionResult(val title: String, val artist: String, val album: String, val spotifyUrl: String?)
