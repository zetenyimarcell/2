package com.hangfolyam.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

// IDE ÍRD A GEMINI API KULCSODAT!
private const val GEMINI_API_KEY = "ITT_A_GEMINI_KULCS"

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
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Főoldal") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Kereső") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Mic, null) }, label = { Text("Felismerő") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") })
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> HomeScreen()
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

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email cím") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Jelszó") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val cleanEmail = email.trim()
                val cleanPassword = password.trim()
                if (cleanEmail.isNotEmpty() && cleanPassword.isNotEmpty()) {
                    if (isSignUp) auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).addOnSuccessListener { onLoginSuccess() }.addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
                    else auth.signInWithEmailAndPassword(cleanEmail, cleanPassword).addOnSuccessListener { onLoginSuccess() }.addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
                } else errorMessage = "Kérjük töltsd ki az összes mezőt!"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isSignUp) "Regisztráció" else "Bejelentkezés") }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) { Text(if (isSignUp) "Van már fiókod? Bejelentkezés" else "Nincs fiókod? Regisztráció") }

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        
        OutlinedButton(
            onClick = {
                if (activity != null) {
                    try {
                        val provider = OAuthProvider.newBuilder("google.com").build()
                        auth.startActivityForSignInWithProvider(activity, provider).addOnSuccessListener { onLoginSuccess() }.addOnFailureListener { errorMessage = "Google hiba: ${it.localizedMessage}" }
                    } catch (e: Exception) { errorMessage = "Hiba: ${e.localizedMessage}" }
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
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Üdvözöllek a Hangfolyam-ban!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Keress zenéket vagy azonosítsd a hallott dalokat a mikrofonnal! Az AI gondoskodik a pontos találatokról.", color = Color.Gray)
    }
}

data class Song(val title: String, val artist: String, val videoId: String)

@Composable
fun SearchScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    var activeSongIndex by remember { mutableStateOf<Int?>(null) }
    var currentLyrics by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer != null && exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition.toFloat()
                duration = exoPlayer.duration.coerceAtLeast(1L).toFloat()
                isPlaying = true
            } else isPlaying = false
            delay(500)
        }
    }

    fun playSong(index: Int) {
        val song = searchResults[index]
        activeSongIndex = index
        currentLyrics = "Audio stream kapcsolódás..."
        exoPlayer?.stop()
        
        coroutineScope.launch {
            val audioUrl = fetchAudioStreamUrl(song.videoId)
            if (audioUrl != null) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.setMediaItem(MediaItem.fromUri(audioUrl))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
                currentLyrics = fetchLyrics(song.artist, song.title) ?: "Nincs elérhető dalszöveg."
            } else currentLyrics = "Hiba: Egyik stream szerver sem válaszol."
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés (pl. Azahriah)...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        statusText = "AI optimalizálás és keresés..."
                        coroutineScope.launch {
                            val targetQuery = optimizeSearchWithGemini(query)
                            val results = searchYouTubeDirectly(targetQuery)
                            searchResults = results
                            isSearching = false
                            statusText = if (results.isEmpty()) "Nincs találat a YouTube-on." else null
                        }
                    }) { Icon(Icons.Default.Search, contentDescription = "Keresés") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        if (statusText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(statusText!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (activeSongIndex != null && searchResults.isNotEmpty()) {
            val song = searchResults[activeSongIndex!!]
            Card(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(song.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(value = currentPosition, onValueChange = { newVal ->
                        currentPosition = newVal
                        exoPlayer?.seekTo(newVal.toLong())
                    }, valueRange = 0f..duration, modifier = Modifier.fillMaxWidth().height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = { playSong((activeSongIndex!! + 1) % searchResults.size) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.height(80.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(currentLyrics ?: "Betöltés...", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults.indices.toList()) { index ->
                    val song = searchResults[index]
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { playSong(index) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(song.artist, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// GEMINI AI - MOST MÁR A ZENEFELISMERŐ IS HASZNÁLJA
suspend fun optimizeSearchWithGemini(userQuery: String, contextStr: String = ""): String = withContext(Dispatchers.IO) {
    if (GEMINI_API_KEY.isBlank() || !GEMINI_API_KEY.startsWith("AIza")) return@withContext userQuery 
    
    try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"
        val prompt = if (contextStr.isNotEmpty()) {
            "A zenefelismerő ezt találta: '$userQuery'. $contextStr. Javítsd ki az esetleges hibákat és add vissza a hivatalos Előadó - Cím formátumot, semmi más sallangot ne írj."
        } else {
            "Felhasználói keresés: '$userQuery'. Alakítsd át ezt egy pontos YouTube zenei keresőkifejezéssé (csak előadó és cím, semmi extra szöveg)."
        }
        
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        }
        val request = Request.Builder().url(url).post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        val response = sharedHttpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext userQuery
            val result = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            if (!result.isNullOrBlank()) return@withContext result.trim()
        }
    } catch (_: Exception) {}
    return@withContext userQuery
}

// ÚJ: KÖZVETLEN YOUTUBE HTML KAPARÁS (SOHA NEM MONDJA HOGY NINCS TALÁLAT, HA LÉTEZIK)
suspend fun searchYouTubeDirectly(query: String): List<Song> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://www.youtube.com/results?search_query=$encodedQuery")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
            
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val html = response.body?.string() ?: return@withContext emptyList()
            val list = mutableListOf<Song>()
            val seenIds = mutableSetOf<String>()
            
            // Közvetlenül a YouTube forráskódjából olvassa ki az ID-kat és Címeket
            val regex = "\"videoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}".toRegex()
            val matches = regex.findAll(html)
            
            for (match in matches) {
                val videoId = match.groupValues[1]
                var title = match.groupValues[2]
                title = title.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "")
                
                if (!seenIds.contains(videoId)) {
                    seenIds.add(videoId)
                    list.add(Song(title, "YouTube Találat", videoId))
                }
                if (list.size >= 12) break
            }
            return@withContext list
        }
    } catch (_: Exception) {}
    return@withContext emptyList()
}

// STABIL STREAM LEKÉRÉS (5 SZERVERREL)
suspend fun fetchAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    val servers = listOf(
        "https://pipedapi.kavin.rocks/streams/$videoId",
        "https://pipedapi.tarba.dev/streams/$videoId",
        "https://api.piped.projectsegfau.lt/streams/$videoId",
        "https://inv.tux.pizza/api/v1/videos/$videoId",
        "https://invidious.nerdvpn.de/api/v1/videos/$videoId"
    )

    for (serverUrl in servers) {
        try {
            val request = Request.Builder().url(serverUrl).header("User-Agent", "Mozilla/5.0").build()
            val response = sharedHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                // PIPED szerverekhez
                val audioStreams = json.optJSONArray("audioStreams")
                if (audioStreams != null && audioStreams.length() > 0) return@withContext audioStreams.getJSONObject(0).optString("url")
                
                // INVIDIOUS szerverekhez
                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.getJSONObject(i)
                        if (fmt.optString("type").contains("audio")) return@withContext fmt.optString("url")
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return@withContext null
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = java.net.URLEncoder.encode(artist.take(20), "UTF-8")
        val cleanTitle = java.net.URLEncoder.encode(title.take(30).replace(Regex("\\(.*\\)"), "").trim(), "UTF-8")
        val response = sharedHttpClient.newCall(Request.Builder().url("https://api.lyrics.ovh/v1/$cleanArtist/$cleanTitle").build()).execute()
        if (response.isSuccessful) return@withContext JSONObject(response.body?.string() ?: "").optString("lyrics", null)
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Koppints a mikrofonra a felismeréshez (5 mp)") }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if (!it) status = "Mikrofon engedély megtagadva!" 
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(120.dp).background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape).clickable {
                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    if (!isListening) {
                        isListening = true
                        status = "Felvétel folyamatban (5 mp)..."
                        coroutineScope.launch {
                            val audioFile = File(context.cacheDir, "temp_record.m4a")
                            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                            try {
                                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                recorder.setOutputFile(audioFile.absolutePath)
                                recorder.prepare()
                                recorder.start()
                                delay(5000)
                                recorder.stop()
                                recorder.release()
                                
                                status = "AudD AI elemzés..."
                                val recognitionResult = recognizeAudioWithAudD(audioFile)
                                if (recognitionResult != null) {
                                    val rawResult = "${recognitionResult.first} ${recognitionResult.second}"
                                    status = "Gemini AI pontosítás: $rawResult..."
                                    
                                    // ITT JÖN BE A KÉPBE AZ AI A ZENEFELISMERŐBE
                                    val optimizedQuery = optimizeSearchWithGemini(rawResult, "Készíts belőle tökéletes YouTube keresőszót")
                                    status = "Keresés: $optimizedQuery"
                                    
                                    val songs = searchYouTubeDirectly(optimizedQuery)
                                    if (songs.isNotEmpty()) {
                                        status = "Lejátszás: ${songs[0].title}"
                                        val streamUrl = fetchAudioStreamUrl(songs[0].videoId)
                                        if (streamUrl != null) {
                                            withContext(Dispatchers.Main) {
                                                exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                                exoPlayer?.prepare()
                                                exoPlayer?.play()
                                            }
                                        } else status = "Hiba: Nem sikerült betölteni a zenét."
                                    } else status = "A felismert dal nem található a YouTube-on."
                                } else status = "Nem sikerült felismerni a zenét."
                            } catch (e: Exception) {
                                status = "Hiba: ${e.message}"
                                try { recorder.release() } catch (_: Exception) {}
                            } finally { isListening = false }
                        }
                    }
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }, contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
    }
}

suspend fun recognizeAudioWithAudD(audioFile: File): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("api_token", "test").addFormDataPart("return", "spotify").addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())).build()
        val response = sharedHttpClient.newCall(Request.Builder().url("https://api.audd.io/").post(requestBody).build()).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            if (json.optString("status") == "success" && !json.isNull("result")) {
                val res = json.getJSONObject("result")
                return@withContext Pair(res.optString("artist", ""), res.optString("title", ""))
            }
        }
    } catch (_: Exception) {}
    finally { if (audioFile.exists()) audioFile.delete() }
    return@withContext null
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = onSignOut) { Text("Kijelentkezés") } }
}
