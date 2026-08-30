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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

// FIGYELEM: Ez a kulcs hibásnak tűnik! A Gemini kulcsok mindig "AIza"-val kezdődnek!
// Kérj egy újat a Google AI Studio-ban: https://aistudio.google.com/app/apikey
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
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, "") }, label = { Text("Főoldal") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Search, "") }, label = { Text("Kereső") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Mic, "") }, label = { Text("Felismerő") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Person, "") }, label = { Text("Profil") })
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
    // (A LoginScreen tartalma változatlan)
    val context = LocalContext.current
    val activity = context as? Activity
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Hangfolyam", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email cím") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Jelszó") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (email.isNotEmpty() && password.isNotEmpty()) {
                if (isSignUp) auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }.addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
                else auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }.addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
            } else errorMessage = "Kérjük töltsd ki a mezőket!"
        }, modifier = Modifier.fillMaxWidth()) { Text(if (isSignUp) "Regisztráció" else "Bejelentkezés") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) { Text(if (isSignUp) "Van már fiókod? Bejelentkezés" else "Nincs fiókod? Regisztráció") }
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
        currentLyrics = "Stream keresése (Több szerver próbálkozása)..."
        exoPlayer?.stop()
        
        coroutineScope.launch {
            // TÖBB SZERVERES TARTALÉK MEGOLDÁS A LEJÁTSZÁSRA
            val pipedServers = listOf(
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.tarba.dev",
                "https://api.piped.projectsegfau.lt"
            )
            
            var playUrl = ""
            for (server in pipedServers) {
                try {
                    val streamRequest = Request.Builder().url("$server/streams/${song.audioUrl}").build()
                    val streamResponse = withContext(Dispatchers.IO) { sharedHttpClient.newCall(streamRequest).execute() }
                    
                    if (streamResponse.isSuccessful) {
                        val streamJson = JSONObject(streamResponse.body?.string() ?: "")
                        val audioStreams = streamJson.optJSONArray("audioStreams")
                        
                        if (audioStreams != null && audioStreams.length() > 0) {
                            playUrl = audioStreams.getJSONObject(0).optString("url")
                            break // Sikerült megtalálni, kilép a ciklusból
                        }
                    }
                } catch (e: Exception) {
                    continue // Ha hiba van a szerverrel, megy a következőre
                }
            }
            
            if (playUrl.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
                currentLyrics = "Dalszöveg betöltése..."
                val lyrics = fetchLyrics(song.artist, song.title)
                currentLyrics = lyrics ?: "Nincs elérhető dalszöveg."
            } else {
                currentLyrics = "Hiba: Egyik stream szerver sem válaszolt. Próbáld újra később!"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Írj be bármit (pl. Azahriah legújabb száma)...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        aiStatus = "Gemini AI: Kapcsolódás..."
                        coroutineScope.launch {
                            try {
                                val optimizedQuery = optimizeSearchWithGemini(query)
                                aiStatus = "YouTube Direkt Keresés: '$optimizedQuery'"
                                searchResults = searchYouTubeDirectly(optimizedQuery)
                                
                                if (searchResults.isEmpty()) {
                                    aiStatus = "Nincs találat a YouTube-on."
                                } else {
                                    aiStatus = null
                                }
                            } catch (e: Exception) {
                                aiStatus = "HIBA: ${e.message}"
                            } finally {
                                isSearching = false
                            }
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
            Text(aiStatus!!, fontSize = 13.sp, color = if (aiStatus!!.contains("HIBA")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
                    Text(song.artist, fontSize = 12.sp, color = Color.Gray)
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
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Lejátszás/Szünet", modifier = Modifier.size(36.dp))
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(aiStatus ?: "Keresés...", color = Color.Gray)
                }
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

// JAVÍTOTT GEMINI AI HIVÁS HIBADOBÁSSAL
suspend fun optimizeSearchWithGemini(userQuery: String): String = withContext(Dispatchers.IO) {
    if (!GEMINI_API_KEY.startsWith("AIza")) {
        throw Exception("Érvénytelen Gemini API kulcs! A kulcsnak 'AIza'-val kell kezdődnie.")
    }
    
    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"
    val prompt = "Te egy zenei keresősegéd vagy. A felhasználó ezt írta be: '$userQuery'. Alakítsd át ezt egy pontos és tiszta YouTube zenei keresőkifejezéssé (csak az előadót és a dalcímet add vissza, semmi felesleges magyarázatot)."
    
    val jsonBody = JSONObject().apply {
        put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
    }

    val request = Request.Builder().url(url).post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
    
    val response = sharedHttpClient.newCall(request).execute()
    if (!response.isSuccessful) {
        val errorText = response.body?.string() ?: ""
        throw Exception("Gemini hiba (${response.code}): Ellenőrizd az API kulcsot!")
    }
    
    val body = response.body?.string() ?: return@withContext userQuery
    val json = JSONObject(body)
    return@withContext json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", userQuery)?.trim() ?: userQuery
}

// ÚJ: KÖZVETLENÜL A WWW.YOUTUBE.COM-RÓL KERES (Nincs több letiltott Piped szerver kereséskor)
suspend fun searchYouTubeDirectly(query: String): List<Song> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "hu-HU,hu;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
            
        val response = sharedHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Nem sikerült elérni a YouTube-ot")
        
        val html = response.body?.string() ?: return@withContext emptyList()
        
        val list = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()
        
        // Reguláris kifejezés, ami közvetlenül a HTML-ből kihúzza a videó azonosítóját és a címét
        val regex = "\"videoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}".toRegex()
        val matches = regex.findAll(html)
        
        for (match in matches) {
            val videoId = match.groupValues[1]
            var title = match.groupValues[2]
            // Tisztítjuk a címet az egyedi HTML kódoktól
            title = title.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "")
            
            if (!seenIds.contains(videoId)) {
                seenIds.add(videoId)
                list.add(Song(title, "YouTube.com Találat", videoId))
            }
            if (list.size >= 15) break // Maximum 15 találatot töltünk be
        }
        
        return@withContext list
    } catch (e: Exception) {
        throw Exception("Hiba a YouTube kaparásakor: ${e.message}")
    }
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = java.net.URLEncoder.encode(artist.take(20), "UTF-8")
        val cleanTitle = java.net.URLEncoder.encode(title.take(30).replace(Regex("\\(.*\\)"), "").trim(), "UTF-8")
        val request = Request.Builder().url("https://api.lyrics.ovh/v1/$cleanArtist/$cleanTitle").header("User-Agent", "Mozilla/5.0").build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            if (json.has("lyrics")) return@withContext json.optString("lyrics")
        }
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    // (Ide a korábbi felépítés kerül minimális változtatással - Lásd előző blokkok, 
    // a lényeg, hogy hívja az új searchYouTubeDirectly-t)
    // Helytakarékosság végett a teljes AudioRecognizerScreen tartalmát is beszúrom, ahogy fent.
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Koppints a mikrofonra a felismeréshez (5 mp)") }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) status = "Mikrofon engedély megtagadva!"
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
                                
                                status = "Zene felismerése az AudD-vel..."
                                val recognitionResult = recognizeAudioWithAudD(audioFile)
                                if (recognitionResult != null) {
                                    val queryToSearch = "${recognitionResult.first} ${recognitionResult.second}"
                                    status = "Keresés YouTube-on: $queryToSearch"
                                    
                                    val songs = searchYouTubeDirectly(queryToSearch)
                                    if (songs.isNotEmpty()) {
                                        status = "Lejátszás: ${songs[0].title}"
                                        
                                        // Több szerveres lejátszási kísérlet
                                        val pipedServers = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.tarba.dev", "https://api.piped.projectsegfau.lt")
                                        for (server in pipedServers) {
                                            try {
                                                val streamReq = Request.Builder().url("$server/streams/${songs[0].audioUrl}").build()
                                                val streamRes = withContext(Dispatchers.IO) { sharedHttpClient.newCall(streamReq).execute() }
                                                if (streamRes.isSuccessful) {
                                                    val streams = JSONObject(streamRes.body?.string() ?: "").optJSONArray("audioStreams")
                                                    if (streams != null && streams.length() > 0) {
                                                        val playUrl = streams.getJSONObject(0).optString("url")
                                                        withContext(Dispatchers.Main) {
                                                            exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                                                            exoPlayer?.prepare()
                                                            exoPlayer?.play()
                                                        }
                                                        break
                                                    }
                                                }
                                            } catch (e: Exception) { continue }
                                        }
                                    } else status = "Nem található a YouTube-on."
                                } else status = "Nem sikerült felismerni a zenét."
                            } catch (e: Exception) {
                                status = "Hiba: ${e.message}"
                                try { recorder.release() } catch (_: Exception) {}
                            } finally {
                                isListening = false
                            }
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSignOut) { Text("Kijelentkezés") }
    }
}
