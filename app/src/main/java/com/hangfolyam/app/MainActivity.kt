package com.hangfolyam.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNavigation(exoPlayer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@Composable
fun AppNavigation(exoPlayer: ExoPlayer?) {
    var selectedTab by remember { mutableStateOf(0) }
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    if (currentUser == null) {
        LoginScreen(onLoginSuccess = { currentUser = auth.currentUser })
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Főoldal") },
                        label = { Text("Főoldal") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Kereső") },
                        label = { Text("Kereső") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Felismerő") },
                        label = { Text("Felismerő") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> HomeScreen(exoPlayer)
                    1 -> SearchScreen(exoPlayer)
                    2 -> AudioRecognizerScreen(exoPlayer)
                    3 -> ProfileScreen(onSignOut = {
                        auth.signOut()
                        exoPlayer?.stop()
                        currentUser = null
                    })
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hangfolyam", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email cím") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: hiba() }
                    } else {
                        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: hiba() }
                    }
                } else {
                    errorMessage = "Kérjük töltsd ki a mezőket!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { 
            Text(if (isSignUp) "Regisztráció" else "Bejelentkezés") 
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Van már fiókod? Bejelentkezés" else "Nincs fiókod? Regisztráció")
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Visszaállítva a Google bejelentkezés gomb!
        OutlinedButton(
            onClick = {
                if (activity != null) {
                    try {
                        val provider = OAuthProvider.newBuilder("google.com").build()
                        auth.startActivityForSignInWithProvider(activity, provider)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = "Google bejelentkezés sikertelen: ${it.localizedMessage}" }
                    } catch (e: Exception) {
                        errorMessage = "Hiba: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bejelentkezés Google-fiókkal")
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

fun hiba() = "Hiba történt"

@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Üdvözöllek a Hangfolyam ban!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Használd a Kereső fület a dalok megtalálásához, vagy a Felismerőt a hangalapú kereséshez.", color = Color.Gray)
    }
}

data class Song(val title: String, val uploader: String, val identifier: String, val source: String)

@Composable
fun SearchScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    var activeSong by remember { mutableStateOf<Song?>(null) }
    var currentLyrics by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés (pl. rock, pop, vagy dal címe)...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        coroutineScope.launch {
                            searchResults = searchMultipleEngines(query)
                            isSearching = false
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Keresés")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (activeSong != null) {
            Card(modifier = Modifier.fillMaxWidth().height(130.dp).padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Lejátszás: ${activeSong!!.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(currentLyrics ?: "Dalszöveg betöltése...", fontSize = 13.sp)
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            activeSong = song
                            currentLyrics = null
                            Toast.makeText(context, "Dal betöltése...", Toast.LENGTH_SHORT).show()
                            
                            coroutineScope.launch {
                                val streamUrl = fetchAudioStreamUrl(song.identifier, song.source)
                                if (streamUrl != null) {
                                    exoPlayer?.stop()
                                    exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                                val lyrics = fetchLyrics(song.uploader, song.title)
                                currentLyrics = lyrics ?: "Nincs elérhető dalszöveg."
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${song.uploader} • ${song.source}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Több motoros biztonságos keresés tartalék (fallback) elemekkel
suspend fun searchMultipleEngines(query: String): List<Song> = coroutineScope {
    val jamendo = async { searchJamendoSongs(query) }
    val piped = async { fetchPipedSongs(query) }
    
    val combined = mutableListOf<Song>()
    combined.addAll(jamendo.await())
    combined.addAll(piped.await())
    
    // Ha mindkét szerver üres választ adna (pl. hálózatihiba vagy túlterhelés), ne legyen üres a lista, adjunk vissza mintákat
    if (combined.isEmpty()) {
        combined.add(Song("Acoustic Breeze (Ajánlott)", "Jamendo Music", "https://freemusicarchive.org/music/Benjamin_Tissot/Acoustic_Breeze", "Jamendo"))
        combined.add(Song("Sunny (Ajánlott)", "Bensound", "https://www.bensound.com/royalty-free-music/track/sunny", "Jamendo"))
        combined.add(Song("Keresési találat erre: $query", "Online Forrás", query, "YouTube"))
    }
    
    combined
}

suspend fun searchJamendoSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val url = "https://api.jamendo.com/v3.0/tracks/?client_id=56631bade&format=json&limit=10&namesearch=$encodedQuery"
    try {
        val response = sharedHttpClient.newCall(Request.Builder().url(url).build()).execute()
        if (response.isSuccessful) {
            val results = JSONObject(response.body?.string() ?: "").optJSONArray("results") ?: return@withContext emptyList()
            val list = mutableListOf<Song>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val audioUrl = item.optString("audio")
                if (audioUrl.isNotEmpty()) list.add(Song(item.optString("name"), item.optString("artist_name"), audioUrl, "Jamendo"))
            }
            return@withContext list
        }
    } catch (_: Exception) {}
    emptyList()
}

suspend fun fetchPipedSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
    val pipedInstances = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.smnz.de", "https://pipedapi.privacydev.net")
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    for (baseUrl in pipedInstances) {
        try {
            val response = sharedHttpClient.newCall(Request.Builder().url("$baseUrl/search?q=$encodedQuery&filter=all").build()).execute()
            if (response.isSuccessful) {
                val items = JSONObject(response.body?.string() ?: "").optJSONArray("items") ?: continue
                val list = mutableListOf<Song>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (item.optString("type") == "stream") {
                        val vId = item.optString("url").replace("/watch?v=", "")
                        if (vId.isNotEmpty()) list.add(Song(item.optString("title"), item.optString("uploaderName"), vId, "YouTube"))
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) {}
    }
    emptyList()
}

suspend fun fetchAudioStreamUrl(identifier: String, source: String): String? = withContext(Dispatchers.IO) {
    if (source == "Jamendo" && identifier.startsWith("http")) return@withContext identifier
    if (source == "Jamendo") return@withContext "https://prod-1.storage.jamendo.com/download/track/$identifier/mp3/1"
    
    val pipedInstances = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.smnz.de")
    for (baseUrl in pipedInstances) {
        try {
            val response = sharedHttpClient.newCall(Request.Builder().url("$baseUrl/streams/$identifier").build()).execute()
            if (response.isSuccessful) {
                val audioStreams = JSONObject(response.body?.string() ?: "").optJSONArray("audioStreams")
                if (audioStreams != null && audioStreams.length() > 0) return@withContext audioStreams.getJSONObject(0).optString("url")
            }
        } catch (_: Exception) {}
    }
    null
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = java.net.URLEncoder.encode(artist.take(20), "UTF-8")
        val cleanTitle = java.net.URLEncoder.encode(title.take(30).replace(Regex("\\(.*\\)"), "").trim(), "UTF-8")
        val url = "https://api.lyrics.ovh/v1/$cleanArtist/$cleanTitle"
        val response = sharedHttpClient.newCall(Request.Builder().url(url).build()).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (json.has("lyrics")) return@withContext json.optString("lyrics")
        }
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Kattints a mikrofonra és mondd be a dal címét!") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val query = matches[0]
                status = "Felismert kifejezés: \"$query\""
                isSearching = true
                coroutineScope.launch {
                    searchResults = searchMultipleEngines(query)
                    isSearching = false
                }
            }
        } else {
            status = "A hangfelismerés megszakadt."
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd be a dal nevét!")
                        }
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "A hangfelismerés nem támogatott ezen az eszközön.", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(70.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(searchResults) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            Toast.makeText(context, "Indítás...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                val streamUrl = fetchAudioStreamUrl(song.identifier, song.source)
                                if (streamUrl != null) {
                                    exoPlayer?.stop()
                                    exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold)
                                Text("${song.uploader} • ${song.source}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSignOut) { Text("Kijelentkezés") }
    }
}
