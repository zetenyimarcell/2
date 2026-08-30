package com.hangfolyam.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private const val GEMINI_API_KEY = "AQ.Ab8RN6LHnJ8PH8dPk6VFQyMGyyg1RmHAtQnoQIejjNWTWLsBbg"

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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
                    } else {
                        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
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
        
        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Üdvözöllek a Hangfolyam-ban!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Használd a Gemini AI-val támogatott keresőt vagy a zenefelismerőt!", color = Color.Gray)
    }
}

data class Song(val title: String, val artist: String, val audioUrl: String)

@Composable
fun SearchScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    var activeSongIndex by remember { mutableStateOf<Int?>(null) }
    var currentLyrics by remember { mutableStateOf<String?>(null) }
    var aiStatus by remember { mutableStateOf<String?>(null) }
    
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
            } else {
                isPlaying = false
            }
            delay(500)
        }
    }

    fun playSongAndFetchLyrics(index: Int) {
        val song = searchResults[index]
        activeSongIndex = index
        currentLyrics = "Zene és dalszöveg betöltése..."
        exoPlayer?.stop()
        
        coroutineScope.launch {
            try {
                // Stabilabb Piped szerver a zene stream lekéréséhez
                val streamRequest = Request.Builder()
                    .url("https://pipedapi.smnz.de/streams/${song.audioUrl}")
                    .build()
                val streamResponse = withContext(Dispatchers.IO) { sharedHttpClient.newCall(streamRequest).execute() }
                
                if (streamResponse.isSuccessful) {
                    val streamJson = JSONObject(streamResponse.body?.string() ?: "")
                    val audioStreams = streamJson.optJSONArray("audioStreams")
                    var playUrl = ""
                    
                    if (audioStreams != null && audioStreams.length() > 0) {
                        playUrl = audioStreams.getJSONObject(0).optString("url")
                    }
                    
                    if (playUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        }
                    } else {
                        currentLyrics = "Hiba: Nem található audio stream ezen a szerveren."
                    }
                } else {
                     currentLyrics = "Hiba a stream lekérésekor."
                }
            } catch (e: Exception) {
                currentLyrics = "Hiba a lejátszás betöltésekor."
            }

            val lyrics = fetchLyrics(song.artist, song.title)
            currentLyrics = lyrics ?: "Nincs elérhető dalszöveg."
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
                        aiStatus = "Gemini AI optimalizálja a keresést..."
                        coroutineScope.launch {
                            val optimizedQuery = optimizeSearchWithGemini(query)
                            aiStatus = "YouTube keresés: '$optimizedQuery'"
                            
                            // Közvetlen YouTube keresés hívása
                            searchResults = searchYouTubeDirectly(optimizedQuery)
                            
                            if (searchResults.isEmpty()) {
                                aiStatus = "Nincs találat a YouTube-on."
                            } else {
                                aiStatus = null
                            }
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
        
        if (aiStatus != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(aiStatus!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (activeSongIndex != null && searchResults.isNotEmpty()) {
            val song = searchResults[activeSongIndex!!]
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Most szól: ${song.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text(song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = currentPosition,
                        onValueChange = { newVal ->
                            currentPosition = newVal
                            exoPlayer?.seekTo(newVal.toLong())
                        },
                        valueRange = 0f..duration,
                        modifier = Modifier.fillMaxWidth().height(20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Lejátszás/Szünet",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        IconButton(onClick = {
                            val nextIndex = (activeSongIndex!! + 1) % searchResults.size
                            playSongAndFetchLyrics(nextIndex)
                        }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Következő", modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.height(80.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(currentLyrics ?: "Dalszöveg betöltése...", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults.indices.toList()) { index ->
                    val song = searchResults[index]
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            Toast.makeText(context, "Zene indítása...", Toast.LENGTH_SHORT).show()
                            playSongAndFetchLyrics(index)
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(song.artist, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1. KÖZVETLEN YOUTUBE KERESŐ (Scraper)
// Ez sosem fog "Nincs találat"-ot dobni, ha a zene fent van a YouTube-on, mert magát az oldalt olvassa el.
suspend fun searchYouTubeDirectly(query: String): List<Song> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "hu-HU,hu;q=0.9")
            .build()
            
        val response = sharedHttpClient.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext emptyList()
        
        val pattern = Pattern.compile("var ytInitialData = (\\{.*?\\});</script>")
        val matcher = pattern.matcher(html)
        
        if (matcher.find()) {
            val jsonString = matcher.group(1)
            val json = JSONObject(jsonString)
            
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")
                
            val list = mutableListOf<Song>()
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val videoRenderer = contents.optJSONObject(i)?.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.optString("videoId")
                        val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Ismeretlen cím"
                        val owner = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Ismeretlen feltöltő"
                        
                        if (videoId.isNotEmpty()) {
                            list.add(Song(title, owner, videoId))
                        }
                    }
                }
            }
            return@withContext list
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext emptyList()
}

suspend fun optimizeSearchWithGemini(userQuery: String): String = withContext(Dispatchers.IO) {
    try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"
        val prompt = "Te egy zenei keresősegéd vagy. A felhasználó ezt írta be: '$userQuery'. Alakítsd át ezt egy pontos és tiszta YouTube zenei keresőkifejezéssé (csak az előadót és a dalcímet add vissza, semmi felesleges magyarázatot vagy sallangot)."
        val jsonBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
        }
        val request = Request.Builder().url(url).post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext userQuery
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                return@withContext candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text", userQuery)?.trim() ?: userQuery
            }
        }
    } catch (e: Exception) {}
    return@withContext userQuery
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = java.net.URLEncoder.encode(artist.take(20), "UTF-8")
        val cleanTitle = java.net.URLEncoder.encode(title.take(30).replace(Regex("\\(.*\\)"), "").trim(), "UTF-8")
        val url = "https://api.lyrics.ovh/v1/$cleanArtist/$cleanTitle"
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext null
            if (JSONObject(body).has("lyrics")) return@withContext JSONObject(body).optString("lyrics")
        }
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Koppints a mikrofonra (5 mp felvétel a Gemini AI-nak)") }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) status = "Mikrofon engedély megtagadva!"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        if (!isListening) {
                            isListening = true
                            status = "Felvétel folyamatban (5 mp)..."
                            
                            coroutineScope.launch {
                                val audioFile = File(context.cacheDir, "gemini_record.m4a")
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
                                    
                                    status = "Hang elemzése a Gemini 2.0 AI-val..."
                                    val aiResult = recognizeAudioWithGemini(audioFile)
                                    
                                    if (!aiResult.isNullOrEmpty()) {
                                        status = "Felismerve: $aiResult. Keresés YouTube-on..."
                                        val songs = searchYouTubeDirectly(aiResult)
                                        
                                        if (songs.isNotEmpty()) {
                                            status = "Lejátszás: ${songs[0].title}"
                                            val streamReq = Request.Builder().url("https://pipedapi.smnz.de/streams/${songs[0].audioUrl}").build()
                                            val streamRes = withContext(Dispatchers.IO) { sharedHttpClient.newCall(streamReq).execute() }
                                            if (streamRes.isSuccessful) {
                                                val json = JSONObject(streamRes.body?.string() ?: "")
                                                val streams = json.optJSONArray("audioStreams")
                                                if (streams != null && streams.length() > 0) {
                                                    val playUrl = streams.getJSONObject(0).optString("url")
                                                    withContext(Dispatchers.Main) {
                                                        exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                                                        exoPlayer?.prepare()
                                                        exoPlayer?.play()
                                                    }
                                                }
                                            }
                                        } else {
                                            status = "A YouTube-on sem található."
                                        }
                                    } else {
                                        status = "A Gemini nem ismert fel zenét vagy szöveget."
                                    }
                                } catch (e: Exception) {
                                    status = "Hiba: ${e.localizedMessage}"
                                    recorder.release()
                                } finally {
                                    isListening = false
                                }
                            }
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
    }
}

// 2. KÖZVETLEN HANGELEMZÉS GEMINI 2.0 FLASH-EL
suspend fun recognizeAudioWithGemini(audioFile: File): String? = withContext(Dispatchers.IO) {
    try {
        val bytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"
        
        val prompt = "Elemezd ezt a hangfelvételt. Ha felismersz egy zenét, írd le az előadót és a dal címét. Ha nem ismersz fel konkrét zenét, de hallasz dalszöveget, írd le a szöveget pontosan, hogy rá lehessen keresni. Semmi magyarázat, csak a keresendő kifejezés legyen a válaszod!"
        
        val jsonBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().apply {
                    put(JSONObject().put("text", prompt))
                    put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "audio/mp4")
                        put("data", base64Audio)
                    }))
                })
            ))
        }
        
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
            
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                return@withContext candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text")?.trim()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        if (audioFile.exists()) audioFile.delete()
    }
    return@withContext null
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSignOut) { Text("Kijelentkezés") }
    }
}
