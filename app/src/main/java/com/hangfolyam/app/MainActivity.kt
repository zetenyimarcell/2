package com.hangfolyam.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ========== ADATMODELLEK ==========
data class LiveSong(
    val id: String = "", 
    val title: String = "", 
    val artist: String = "", 
    val coverUrl: String = "", 
    val streamUrl: String = "",
    val source: String = "Ismeretlen"
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

// ========== HIÁNYZÓ REPOSITORY ÉS SEGÉdek ==========
class CollectionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun add(song: LiveSong) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("favorites").document(song.id.ifEmpty { song.title }).set(song).await()
        } catch (_: Exception) {}
    }

    suspend fun getFavorites(): List<LiveSong> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(uid).collection("favorites").get().await()
            snapshot.toObjects(LiveSong::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            val json = JSONObject(body)
            return@withContext json.optString("lyrics", null)
        }
    } catch (_: Exception) {}
    return@withContext null
}

suspend fun recognizeAudioFile(file: File): LiveSong? = withContext(Dispatchers.IO) {
    // Itt integrálható egy külső zeneazonosító API (pl. AudD), vagy visszaadható egy teszt dal demonstrációhoz
    delay(2000)
    return@withContext LiveSong(
        id = "rec_1",
        title = "Ismert Felismert Dal",
        artist = "Előadó",
        coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150",
        streamUrl = "yt:dQw4w9WgXcQ",
        source = "YouTube"
    )
}

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF05050B),
                surface = Color(0xFF13132B),
                primary = Color(0xFF1DB954),
                secondary = Color(0xFF8A2BE2)
            )) {
                AppRoot(activity = this, clientId = WEB_CLIENT_ID, auth = auth)
            }
        }
    }
}

@Composable
fun AppRoot(activity: ComponentActivity, clientId: String, auth: FirebaseAuth) {
    val context = LocalContext.current
    val currentVersionCode = 1 
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        updateInfo = checkForUpdates(currentVersionCode)
    }

    updateInfo?.let { info ->
        AutoUpdateDialog(context = context, updateInfo = info, onDismiss = { updateInfo = null })
    }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    var currentScreen by remember { mutableStateOf(if (currentUser != null) "HOME" else "LOGIN") }
    LaunchedEffect(currentUser) { currentScreen = if (currentUser != null) "HOME" else "LOGIN" }

    Crossfade(targetState = currentScreen, animationSpec = tween(700), label = "ScreenTransition") { screen ->
        when (screen) {
            "LOGIN" -> LoginScreen(activity, clientId, auth, { currentScreen = "HOME" }, { currentScreen = "PHONE_LOGIN" })
            "PHONE_LOGIN" -> PhoneLoginScreen(activity, auth, { currentScreen = "LOGIN" }, { currentScreen = "HOME" })
            "HOME" -> HomeScreen(auth = auth)
        }
    }
}

suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()
        val jsonUrl = "https://raw.githubusercontent.com/felhasznalo/projekt/main/version.json"
        val request = Request.Builder().url(jsonUrl).build()
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "")
        val remoteVersion = json.getInt("versionCode")

        if (remoteVersion > currentVersionCode) {
            return@withContext UpdateInfo(
                versionCode = remoteVersion,
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                releaseNotes = json.getString("releaseNotes")
            )
        }
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AutoUpdateDialog(context: Context, updateInfo: UpdateInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Új frissítés érhető el! (${updateInfo.versionName})") },
        text = { Text(updateInfo.releaseNotes) },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                    context.startActivity(intent)
                    onDismiss()
                }
            ) { Text("Frissítés letöltése") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Később") }
        }
    )
}

@Composable
fun LoginScreen(activity: ComponentActivity, clientId: String, auth: FirebaseAuth, onLoggedIn: () -> Unit, onPhoneClick: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isLoading = true
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(account?.idToken, null))
                .addOnCompleteListener { if (it.isSuccessful) onLoggedIn() else isLoading = false }
        } catch (_: Exception) {
            isLoading = false
            Toast.makeText(context, "Hiba a bejelentkezés során!", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF23143A), Color(0xFF05050B)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(modifier = Modifier.size(100.dp).shadow(20.dp, CircleShape).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Nova Premium", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Magyar & Nemzetközi zenék tárháza.", fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(64.dp))

            if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            else {
                Button(
                    onClick = { googleLauncher.launch(GoogleSignIn.getClient(activity, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(clientId).requestEmail().build()).signInIntent) },
                    modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Text("Folytatás Google-fiókkal", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onPhoneClick, modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(25.dp)) {
                    Icon(Icons.Default.Phone, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Belépés telefonszámmal", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PhoneLoginScreen(activity: ComponentActivity, auth: FirebaseAuth, onBack: () -> Unit, onSuccess: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var sms by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var vId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF05050B)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!codeSent) "Telefonszámod" else "SMS Kód", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = if (!codeSent) phone else sms, onValueChange = { if (!codeSent) phone = it else sms = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), placeholder = { Text(if (!codeSent) "+3630..." else "123456") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (!codeSent) {
                        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(auth).setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS).setActivity(activity).setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(c: PhoneAuthCredential) { auth.signInWithCredential(c).addOnSuccessListener { onSuccess() } }
                            override fun onVerificationFailed(e: FirebaseException) { codeSent = true }
                            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) { vId = id; codeSent = true }
                        }).build())
                    } else vId?.let { auth.signInWithCredential(PhoneAuthProvider.getCredential(it, sms)).addOnSuccessListener { onSuccess() } }
                }, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (!codeSent) "Kód küldése" else "Belépés") }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Vissza", color = Color.Gray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val repo = remember { CollectionRepository() }

    var currentTab by remember { mutableStateOf("SEARCH") }
    var currentlyPlaying by remember { mutableStateOf<LiveSong?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf("Dalszöveg keresése...") }

    var isPlayerExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    fun playSong(song: LiveSong) {
        currentlyPlaying = song
        isBuffering = true
        lyricsText = "Dalszöveg keresése..."
        scope.launch {
            launch(Dispatchers.IO) {
                lyricsText = fetchLyrics(song.artist, song.title) ?: "Dalszöveg nem található ehhez a dalhoz."
            }

            val playUrl = if (song.streamUrl.startsWith("yt:")) getYouTubeAudioStream(song.streamUrl.removePrefix("yt:")) ?: "" else song.streamUrl
            if (playUrl.isNotEmpty()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(playUrl))
                exoPlayer.prepare()
                exoPlayer.play()
            } else Toast.makeText(context, "Hiba a lejátszáskor.", Toast.LENGTH_SHORT).show()
            isBuffering = false
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentlyPlaying != null && !isPlayerExpanded, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    currentlyPlaying?.let { song ->
                        MiniPlayer(song, exoPlayer, isBuffering) { isPlayerExpanded = true }
                    }
                }
                NavigationBar(containerColor = Color(0xFF0F0F1A), contentColor = Color.White) {
                    NavigationBarItem(selected = currentTab == "SEARCH", onClick = { currentTab = "SEARCH" }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Felfedezés") })
                    NavigationBarItem(selected = currentTab == "RECOGNIZE", onClick = { currentTab = "RECOGNIZE" }, icon = { Text("🎤", fontSize = 18.sp) }, label = { Text("Felismerés") })
                    NavigationBarItem(selected = currentTab == "COLLECTION", onClick = { currentTab = "COLLECTION" }, icon = { Text("❤️", fontSize = 18.sp) }, label = { Text("Gyűjtemény") })
                    NavigationBarItem(selected = currentTab == "PROFILE", onClick = { currentTab = "PROFILE" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Color(0xFF121225), Color(0xFF05050B))))) {
            when (currentTab) {
                "SEARCH" -> SearchScreen({ playSong(it) }, { scope.launch { repo.add(it); Toast.makeText(context, "Mentve a kedvencekhez!", Toast.LENGTH_SHORT).show() } })
                "RECOGNIZE" -> RecognizeScreen { playSong(it) }
                "COLLECTION" -> CollectionScreen(repo) { playSong(it) }
                "PROFILE" -> ProfileScreen(auth)
            }
        }

        if (isPlayerExpanded && currentlyPlaying != null) {
            ModalBottomSheet(
                onDismissRequest = { isPlayerExpanded = false },
                sheetState = sheetState,
                containerColor = Color(0xFF0D0D17),
                modifier = Modifier.fillMaxSize()
            ) {
                FullPlayerScreen(song = currentlyPlaying!!, exoPlayer = exoPlayer, lyrics = lyricsText, onDismiss = { scope.launch { sheetState.hide(); isPlayerExpanded = false } })
            }
        }
    }
}

// ========== TOVÁBBI UI KOMPONENSEK (COLLECTION & PROFILE) ==========
@Composable
fun CollectionScreen(repo: CollectionRepository, onPlay: (LiveSong) -> Unit) {
    var savedSongs by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        savedSongs = repo.getFavorites()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Kedvencek", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else if (savedSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Még nincsenek mentett zenéid.", color = Color.Gray) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedSongs) { song -> SongRow(song, { onPlay(song) }, null) }
            }
        }
    }
}

@Composable
fun ProfileScreen(auth: FirebaseAuth) {
    val user = auth.currentUser
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Profil", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(user?.email ?: user?.phoneNumber ?: "Ismeretlen felhasználó", color = Color.Gray, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { auth.signOut() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("Kijelentkezés", color = Color.White)
        }
    }
}

suspend fun searchMultiEngine(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val encodedQuery = URLEncoder.encode(query, "UTF-8")

    try {
        val ytSongs = mutableListOf<LiveSong>()
        val url = "https://pipedapi.kavin.rocks/search?q=$encodedQuery&filter=all"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "")
        val results = json.optJSONArray("items")
        
        if (results != null && results.length() > 0) {
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("type") == "stream") {
                    val videoId = item.optString("url").removePrefix("/watch?v=")
                    ytSongs.add(LiveSong(id = videoId, title = item.optString("title", "Ismeretlen"), artist = item.optString("uploaderName", "YouTube"), coverUrl = item.optString("thumbnail", ""), streamUrl = "yt:$videoId", source = "YouTube"))
                }
                if (ytSongs.size >= 20) break
            }
        }
        if (ytSongs.isNotEmpty()) return@withContext ytSongs
    } catch (_: Exception) {}

    try {
        val deezerSongs = mutableListOf<LiveSong>()
        val url = "https://api.deezer.com/search?q=$encodedQuery&limit=20"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "")
        val data = json.optJSONArray("data")
        
        if (data != null && data.length() > 0) {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                deezerSongs.add(LiveSong(id = item.optString("id"), title = item.optString("title"), artist = item.getJSONObject("artist").optString("name"), coverUrl = item.getJSONObject("album").optString("cover_xl", ""), streamUrl = item.optString("preview", ""), source = "Deezer"))
            }
        }
        if (deezerSongs.isNotEmpty()) return@withContext deezerSongs
    } catch (_: Exception) {}

    return@withContext emptyList()
}

suspend fun getYouTubeAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val url = "https://pipedapi.kavin.rocks/streams/$videoId"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "")
        if (json.has("audioStreams")) {
            val audioStreams = json.getJSONArray("audioStreams")
            if (audioStreams.length() > 0) return@withContext audioStreams.getJSONObject(0).getString("url")
        }
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun SearchScreen(onPlay: (LiveSong) -> Unit, onSave: (LiveSong) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Mit hallgatnál?", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = { 
                query = it
                if (it.length >= 2) {
                    isLoading = true
                    scope.launch { results = searchMultiEngine(it); isLoading = false }
                } else results = emptyList()
            },
            modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)),
            placeholder = { Text("Magyar vagy külföldi dal, előadó...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E35), unfocusedContainerColor = Color(0xFF1E1E35), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(results) { song -> SongRow(song, { onPlay(song) }, { onSave(song) }) }
            }
        }
    }
}

@Composable
fun SongRow(song: LiveSong, onClick: () -> Unit, onSave: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF161625)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.coverUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150" }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.DarkGray.copy(alpha=0.5f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(when(song.source) { "YouTube" -> "📺"; "Deezer" -> "🎧"; else -> "💿" }, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(song.source, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        if (onSave != null) { IconButton(onClick = onSave) { Text("❤️", fontSize = 22.sp) } }
    }
}

@Composable
fun MiniPlayer(song: LiveSong, exoPlayer: ExoPlayer, isBuffering: Boolean, onClick: () -> Unit) {
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    LaunchedEffect(song) { while (true) { isPlaying = exoPlayer.isPlaying; delay(300) } }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).shadow(15.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFF202030)).clickable { onClick() }) {
        if (isBuffering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, maxLines = 1)
                Text(song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerScreen(song: LiveSong, exoPlayer: ExoPlayer, lyrics: String, onDismiss: () -> Unit) {
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(song) {
        while (true) {
            if (!seeking) { duration = exoPlayer.duration.coerceAtLeast(1L).toFloat(); position = exoPlayer.currentPosition.toFloat() }
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C2C40), Color(0xFF05050B)))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Text("▼", color = Color.White, fontSize = 24.sp) }
            Text("MOST JÁTSZOTT", color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { }) { Text("⋮", color = Color.White, fontSize = 24.sp) }
        }
        Spacer(Modifier.height(32.dp))
        
        val infiniteTransition = rememberInfiniteTransition(label = "ScaleAnim")
        val scale by infiniteTransition.animateFloat(initialValue = 0.98f, targetValue = 1.02f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "Scale")
        AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(300.dp).scale(if(isPlaying) scale else 1f).shadow(30.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
        
        Spacer(Modifier.height(40.dp))
        
        Text(song.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(song.artist, fontSize = 18.sp, color = Color.LightGray, maxLines = 1)
        
        Spacer(Modifier.height(24.dp))
        
        Slider(
            value = position, valueRange = 0f..duration,
            onValueChange = { seeking = true; position = it },
            onValueChangeFinished = { exoPlayer.seekTo(position.toLong()); seeking = false },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.DarkGray)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toLong()), color = Color.Gray, fontSize = 12.sp)
            Text(formatTime(duration.toLong()), color = Color.Gray, fontSize = 12.sp)
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("⏮", fontSize = 32.sp, color = Color.White)
            Box(modifier = Modifier.size(70.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }, contentAlignment = Alignment.Center) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 32.sp, color = Color.Black)
            }
            Text("⏭", fontSize = 32.sp, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E35).copy(alpha=0.8f))) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Dalszöveg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text(lyrics, color = Color.LightGray, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun RecognizeScreen(onPlay: (LiveSong) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var resultSong by remember { mutableStateOf<LiveSong?>(null) }
    var statusText by remember { mutableStateOf("Koppints a gombra a felismeréshez") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecognitionProcess(context, scope, { isListening = it }, { statusText = it }, { resultSong = it })
        } else {
            Toast.makeText(context, "Mikrofon engedély szükséges a felismeréshez!", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Zenefelismerés", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(statusText, fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(if (isListening) pulseScale else 1f)
                .shadow(30.dp, CircleShape)
                .clip(CircleShape)
                .background(if (isListening) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                .clickable {
                    if (!isListening) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecognitionProcess(context, scope, { isListening = it }, { statusText = it }, { resultSong = it })
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("🎤", fontSize = 64.sp)
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (isListening) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        resultSong?.let { song ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E35))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TALÁLAT!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    SongRow(song = song, onClick = { onPlay(song) }, onSave = null)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onPlay(song) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Dal Lejátszása", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

private fun startRecognitionProcess(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    setListening: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setResult: (LiveSong?) -> Unit
) {
    setListening(true)
    setStatus("Környezeti hangok rögzítése (5 mp)...")
    setResult(null)

    val outputFile = File(context.cacheDir, "audio_sample.3gp")
    var recorder: MediaRecorder? = null

    try {
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    } catch (_: Exception) {
        setListening(false)
        setStatus("Hiba a mikrofon indításakor!")
        return
    }

    scope.launch(Dispatchers.IO) {
        delay(5000)
        try {
            recorder.stop()
            recorder.release()
        } catch (_: Exception) {}

        withContext(Dispatchers.Main) { setStatus("Hangminta elemzése az adatbázisban...") }

        val song = recognizeAudioFile(outputFile)

        withContext(Dispatchers.Main) {
            setListening(false)
            if (song != null) {
                setStatus("Sikeres felismerés!")
                setResult(song)
            } else {
                setStatus("Nem sikerült azonosítani a dalt. Próbáld újra!")
            }
        }
    }
}
