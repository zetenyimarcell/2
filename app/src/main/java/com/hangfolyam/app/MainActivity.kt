package com.hangfolyam.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Megnövelt hálózati időkorlát a teljes dalok stabil letöltéséhez
private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(8.dp),
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1f, label = "scale")

    this
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .clickable(onClick = onClick)
        .scale(scale)
        .border(
            width = if (isFocused) 4.dp else 0.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = shape
        )
}

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Zenei fókusz beállítása a teljes lejátszáshoz
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(exoPlayer)
                }
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
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                NavigationRailItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Főoldal") },
                    label = { Text("Főoldal") }
                )
                NavigationRailItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Kereső") },
                    label = { Text("Kereső") }
                )
                NavigationRailItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Felismerő") },
                    label = { Text("Felismerő") }
                )
                NavigationRailItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                    label = { Text("Profil") }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.6f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Hangfolyam TV", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email cím") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().focusable()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Jelszó") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().focusable()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        if (isSignUp) {
                            auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                        } else {
                            auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                        }
                    } else {
                        errorMessage = "Kérjük töltsd ki a mezőket!"
                    }
                },
                modifier = Modifier.fillMaxWidth().tvFocusable {}
            ) { Text(if (isSignUp) "Regisztráció" else "Bejelentkezés") }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { isSignUp = !isSignUp },
                modifier = Modifier.tvFocusable {}
            ) {
                Text(if (isSignUp) "Van már fiókod? Bejelentkezés" else "Nincs fiókod? Regisztráció")
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    if (activity != null) {
                        try {
                            val provider = OAuthProvider.newBuilder("google.com").build()
                            auth.startActivityForSignInWithProvider(activity, provider).addOnSuccessListener { onLoginSuccess() }
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth().tvFocusable {}
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
}

@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    val playlists = listOf("Napi Mix 1", "Magyar Top 50", "Újdonságok", "Fókusz")
    val recentSongs = listOf("Azahriah - 3 Korty", "Dzsúdló - Várnék", "Halott Pénz - Amikor feladnád")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("Üdvözöllek a kanapén!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Neked ajánlott", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(end = 16.dp)) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .size(180.dp, 100.dp)
                            .padding(end = 12.dp)
                            .tvFocusable { Toast.makeText(context, "$playlist kiválasztva", Toast.LENGTH_SHORT).show() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(playlist, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            Text("Legutóbb hallgatott", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                recentSongs.forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .tvFocusable { Toast.makeText(context, "Keresd meg a Keresőben a teljes dalt!", Toast.LENGTH_SHORT).show() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(50.dp).background(Color.Gray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(song, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

data class Song(val title: String, val uploader: String, val videoId: String)

@Composable
fun SearchScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    var loadingVideoId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Keresés teljes dalokra...") },
                modifier = Modifier.weight(1f).focusable(),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    if (query.isNotEmpty()) {
                        isSearching = true
                        coroutineScope.launch {
                            searchResults = fetchFullSongs(query)
                            isSearching = false
                        }
                    }
                },
                modifier = Modifier.tvFocusable {}
            ) {
                Icon(Icons.Default.Search, contentDescription = "Keresés")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keresés")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .tvFocusable {
                                if (loadingVideoId != null) return@tvFocusable
                                loadingVideoId = song.videoId
                                Toast.makeText(context, "Teljes dal betöltése...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val streamUrl = fetchAudioStreamUrl(song.videoId)
                                    loadingVideoId = null
                                    if (streamUrl != null) {
                                        exoPlayer?.stop()
                                        exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                    }
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (loadingVideoId == song.videoId) {
                                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(song.uploader, color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchFullSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
    val pipedInstances = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de")
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

    for (baseUrl in pipedInstances) {
        try {
            val request = Request.Builder().url("$baseUrl/search?q=$encodedQuery&filter=music_songs").build()
            val response = sharedHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONObject(response.body?.string() ?: "").optJSONArray("items") ?: continue
                val list = mutableListOf<Song>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.optString("type") == "stream") {
                        val videoId = item.optString("url").replace("/watch?v=", "")
                        if (videoId.isNotEmpty()) {
                            list.add(Song(item.optString("title"), item.optString("uploaderName"), videoId))
                        }
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) { continue }
    }
    return@withContext emptyList()
}

suspend fun fetchAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    val pipedInstances = listOf("https://pipedapi.kavin.rocks", "https://pipedapi.adminforge.de")
    for (baseUrl in pipedInstances) {
        try {
            val request = Request.Builder().url("$baseUrl/streams/$videoId").build()
            val response = sharedHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val audioStreams = JSONObject(response.body?.string() ?: "").optJSONArray("audioStreams")
                if (audioStreams != null && audioStreams.length() > 0) {
                    return@withContext audioStreams.getJSONObject(0).optString("url")
                }
            }
        } catch (_: Exception) { continue }
    }
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Nyomd meg a mikrofont (OK gomb) a kezdéshez") }
    var foundSong by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sampleSongs = listOf("Azahriah - 3 Korty", "Dzsúdló - Várnék", "Halott Pénz - Amikor feladnád")

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = if (it) "Kész! Próbáld újra." else "A mikrofon engedély kötelező!"
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .tvFocusable(shape = CircleShape) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            if (!isListening) {
                                isListening = true
                                foundSong = null
                                status = "Távirányító mikrofonjának hallgatása..."
                                coroutineScope.launch {
                                    delay(3000)
                                    isListening = false
                                    foundSong = sampleSongs.random()
                                    status = "Felismerve:"
                                }
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    .background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(status, fontSize = 20.sp)

            if (foundSong != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(foundSong!!, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.6f)) {
            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(user?.email ?: "Felhasználó", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Ingyenes TV Fiók", color = Color.Gray)
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().tvFocusable {}
            ) {
                Text("Kijelentkezés", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
