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
import androidx.compose.foundation.lazy.LazyRow
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
                        label = { Text("Főoldal") },
                        modifier = Modifier.focusable()
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Kereső") },
                        label = { Text("Kereső") },
                        modifier = Modifier.focusable()
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Felismerő") },
                        label = { Text("Felismerő") },
                        modifier = Modifier.focusable()
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") },
                        modifier = Modifier.focusable()
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
    // A bejelentkező kód változatlan maradt a korábbiakhoz képest...
    val context = LocalContext.current
    val activity = context as? Activity
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hangfolyam", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth().focusable())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Jelszó") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().focusable())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (isSignUp) auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                else auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
            },
            modifier = Modifier.fillMaxWidth().focusable()
        ) { Text(if (isSignUp) "Regisztráció" else "Bejelentkezés") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }, modifier = Modifier.focusable()) {
            Text(if (isSignUp) "Váltás Bejelentkezésre" else "Váltás Regisztrációra")
        }
    }
}

@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    // (A korábbi kezdőképernyő kódja)
    Text("Üdvözöllek!", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
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
            label = { Text("Keresés több hálózaton egyszerre...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        coroutineScope.launch {
                            searchResults = searchMultipleEngines(query)
                            isSearching = false
                        }
                    }, modifier = Modifier.focusable()) {
                        Icon(Icons.Default.Search, contentDescription = "Keresés")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusable(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dalszöveg / Lejátszó panel
        if (activeSong != null) {
            Card(modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 8.dp).focusable()) {
                Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text("Lejátszás: ${activeSong!!.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(currentLyrics ?: "Dalszöveg lekérése a Lyrics.ovh szerverről...", fontSize = 14.sp)
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).focusable().clickable {
                            activeSong = song
                            currentLyrics = null
                            Toast.makeText(context, "Dal betöltése (${song.source})...", Toast.LENGTH_SHORT).show()
                            
                            coroutineScope.launch {
                                // 1. Zene indítása
                                val streamUrl = fetchAudioStreamUrl(song.identifier, song.source)
                                if (streamUrl != null) {
                                    exoPlayer?.stop()
                                    exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                }
                                
                                // 2. Dalszöveg keresése automatikusan a Lyrics.ovh API-val
                                val lyrics = fetchLyrics(song.uploader, song.title)
                                currentLyrics = lyrics ?: "Nem található dalszöveg ehhez a dalhoz."
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

// ================= API HÍVÁSOK ÉS MOTOROK =================

suspend fun searchMultipleEngines(query: String): List<Song> = coroutineScope {
    // Párhuzamos keresés elindítása (Jamendo + YouTube proxy)
    val jamendo = async { searchJamendoSongs(query) }
    val piped = async { fetchPipedSongs(query) }
    
    val combined = mutableListOf<Song>()
    combined.addAll(jamendo.await())
    combined.addAll(piped.await())
    
    // Véletlenszerű keverés, hogy mindkét motor eredményei látszódjanak a lista tetején
    combined.shuffled()
}

// Jamendo keresés (Ingyenes, hivatalos MP3)
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

// YouTube proxy keresés
suspend fun fetchPipedSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
    val pipedInstances = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.smnz.de")
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

// Zenei URL kibontása forrás alapján
suspend fun fetchAudioStreamUrl(identifier: String, source: String): String? = withContext(Dispatchers.IO) {
    if (source == "Jamendo") return@withContext identifier
    
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

// Lyrics.ovh API hívás a JSON válasz feldolgozásához
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

// ================= VALÓDI HANGFELSZIMERŐ (SPEECH-TO-TEXT) =================

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Nyomd meg a mikrofont és mondd be a dal címét vagy egy részletét!") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Beépített Android hangfelismerő (SpeechRecognizer) indítója
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val query = matches[0]
                status = "Felismert szöveg: \"$query\"\nKeresés folyamatban..."
                isSearching = true
                coroutineScope.launch {
                    searchResults = searchMultipleEngines(query)
                    isSearching = false
                    if (searchResults.isEmpty()) status = "Nincs találat a \"$query\" kifejezésre."
                    else status = "Eredmények a \"$query\" szövegre:"
                }
            }
        } else {
            status = "Hangfelismerés megszakítva."
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .focusable()
                .clickable {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd be a dal címét vagy szövegét!")
                        }
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "A hangfelismerő nem elérhető ezen a TV-n/eszközön.", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Felismerés indítása", tint = Color.White, modifier = Modifier.size(64.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(searchResults) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).focusable().clickable {
                            Toast.makeText(context, "Dal betöltése...", Toast.LENGTH_SHORT).show()
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
        Button(onClick = onSignOut, modifier = Modifier.focusable()) { Text("Kijelentkezés") }
    }
}
