package com.hangfolyam.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ========== SPOTIFY RAPIDAPI CONFIG ==========
private const val RAPIDAPI_KEY = "2d254d6e90msh93b4b9705fc3e35p1af4c0jsna208abe5a1fc"
private const val RAPIDAPI_HOST = "spotify23.p.rapidapi.com"

// ========== ADATMODELLEK ==========
data class LiveSong(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val streamUrl: String = "",
    val source: String = "Spotify"
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

// ========== YOUTUBE / PIPED TELJES AUDIO MOTOR ==========
suspend fun fetchFullYoutubeAudioUrl(artist: String, title: String): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
        
    val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
    val searchQuery = URLEncoder.encode("$artist $cleanTitle audio", "UTF-8")
    
    val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.yt.artemislena.eu",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.projectsegfau.lt",
        "https://api.piped.private.coffee"
    )

    for (instance in pipedInstances) {
        try {
            val searchUrl = "$instance/search?q=$searchQuery&filter=music_songs"
            val searchReq = Request.Builder().url(searchUrl).build()
            val searchResp = client.newCall(searchReq).execute()

            if (searchResp.isSuccessful) {
                val searchBody = searchResp.body?.string() ?: continue
                val items = JSONObject(searchBody).optJSONArray("items")

                if (items != null && items.length() > 0) {
                    val limit = minOf(items.length(), 3)
                    for (i in 0 until limit) {
                        val itemObj = items.getJSONObject(i)
                        val urlField = itemObj.optString("url", "")
                        val videoId = if (urlField.contains("watch?v=")) {
                            urlField.substringAfter("watch?v=")
                        } else {
                            itemObj.optString("videoId", "")
                        }

                        if (videoId.isNotBlank()) {
                            val streamsUrl = "$instance/streams/$videoId"
                            val streamsReq = Request.Builder().url(streamsUrl).build()
                            val streamsResp = client.newCall(streamsReq).execute()

                            if (streamsResp.isSuccessful) {
                                val streamsBody = streamsResp.body?.string() ?: continue
                                val audioStreams = JSONObject(streamsBody).optJSONArray("audioStreams")

                                if (audioStreams != null && audioStreams.length() > 0) {
                                    for (j in 0 until audioStreams.length()) {
                                        val audioObj = audioStreams.getJSONObject(j)
                                        val audioUrl = audioObj.optString("url", "")
                                        if (audioUrl.isNotBlank()) {
                                            return@withContext audioUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return@withContext ""
}

// ========== SPOTIFY KERESŐMOTOR ==========
suspend fun searchSpotifyAPI(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    if (query.trim().isEmpty()) return@withContext emptyList()

    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build()
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val url = "https://$RAPIDAPI_HOST/search/?q=$encodedQuery&type=tracks&offset=0&limit=20"

    val request = Request.Builder()
        .url(url)
        .addHeader("x-rapidapi-key", RAPIDAPI_KEY)
        .addHeader("x-rapidapi-host", RAPIDAPI_HOST)
        .build()

    try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val tracksObj = json.optJSONObject("tracks") ?: return@withContext emptyList()
            val itemsArray = tracksObj.optJSONArray("items") ?: return@withContext emptyList()

            val songsList = mutableListOf<LiveSong>()
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val data = item.optJSONObject("data") ?: item

                val rawId = data.optString("id", "")
                val trackId = rawId.substringAfterLast(":")
                val title = data.optString("name", "Ismeretlen dal")
                
                var artistName = "Ismeretlen előadó"
                val artistsObj = data.optJSONObject("artists")
                if (artistsObj != null) {
                    val artistItems = artistsObj.optJSONArray("items")
                    if (artistItems != null && artistItems.length() > 0) {
                        artistName = artistItems.getJSONObject(0).optJSONObject("profile")?.optString("name", "Ismeretlen előadó") ?: "Ismeretlen előadó"
                    }
                }

                var coverUrl = ""
                val albumObj = data.optJSONObject("albumOfTrack") ?: data.optJSONObject("album")
                val coverArt = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                if (coverArt != null && coverArt.length() > 0) {
                    coverUrl = coverArt.getJSONObject(0).optString("url", "")
                }

                songsList.add(
                    LiveSong(
                        id = trackId,
                        title = title,
                        artist = artistName,
                        coverUrl = coverUrl,
                        streamUrl = "",
                        source = "Spotify"
                    )
                )
            }
            return@withContext songsList
        }
    } catch (_: Exception) {}
    return@withContext emptyList()
}

suspend fun fetchSpotifyLyrics(trackId: String): String? = withContext(Dispatchers.IO) {
    if (trackId.isEmpty()) return@withContext "Dalszöveg nem érhető el."
    
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build()
    val url = "https://$RAPIDAPI_HOST/track_lyrics/?id=$trackId"

    val request = Request.Builder()
        .url(url)
        .addHeader("x-rapidapi-key", RAPIDAPI_KEY)
        .addHeader("x-rapidapi-host", RAPIDAPI_HOST)
        .build()

    try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val lyricsObj = json.optJSONObject("lyrics")
            val linesArray = lyricsObj?.optJSONArray("lines")
            
            if (linesArray != null) {
                val lyricsBuilder = StringBuilder()
                for (i in 0 until linesArray.length()) {
                    val line = linesArray.getJSONObject(i)
                    lyricsBuilder.append(line.optString("words", "")).append("\n")
                }
                return@withContext lyricsBuilder.toString()
            }
        }
    } catch (_: Exception) {}
    return@withContext "Dalszöveg nem található ehhez a számhoz."
}

// ========== ADATBÁZIS ÉS REPOSITORY ==========
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

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            val context = LocalContext.current
            val playerViewModel: PlayerViewModel = viewModel()
            playerViewModel.initPlayer(context)

            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF05050B),
                surface = Color(0xFF13132B),
                primary = Color(0xFF1DB954),
                secondary = Color(0xFF8A2BE2)
            )) {
                AppRoot(activity = this, clientId = WEB_CLIENT_ID, auth = auth, playerViewModel = playerViewModel)
            }
        }
    }
}

@Composable
fun AppRoot(activity: ComponentActivity, clientId: String, auth: FirebaseAuth, playerViewModel: PlayerViewModel) {
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
            "HOME" -> HomeScreen(auth = auth, playerViewModel = playerViewModel)
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

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF142B20), Color(0xFF05050B)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(modifier = Modifier.size(100.dp).shadow(20.dp, CircleShape).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Spotify Nova", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Teljes hosszúságú lejátszás és dalszövegek.", fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center)
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
fun HomeScreen(auth: FirebaseAuth, playerViewModel: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CollectionRepository() }

    var currentTab by remember { mutableStateOf("SEARCH") }
    var currentlyPlaying by remember { mutableStateOf<LiveSong?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var lyricsText by remember { mutableStateOf("Dalszöveg betöltése...") }

    var isPlayerExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun playSong(song: LiveSong) {
        currentlyPlaying = song
        isBuffering = true
        lyricsText = "Dalszöveg betöltése..."
        
        scope.launch {
            launch(Dispatchers.IO) {
                lyricsText = fetchSpotifyLyrics(song.id) ?: "Dalszöveg nem érhető el."
            }

            val fullAudioStreamUrl = fetchFullYoutubeAudioUrl(song.artist, song.title)

            if (fullAudioStreamUrl.isNotBlank()) {
                playerViewModel.playAudio(fullAudioStreamUrl)
            } else {
                Toast.makeText(context, "Hiba a lejátszási hivatkozás betöltésekor.", Toast.LENGTH_SHORT).show()
            }
            isBuffering = false
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentlyPlaying != null && !isPlayerExpanded, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    currentlyPlaying?.let { song ->
                        MiniPlayer(song, playerViewModel.player, isBuffering) { isPlayerExpanded = true }
                    }
                }
                NavigationBar(containerColor = Color(0xFF0F0F1A), contentColor = Color.White) {
                    NavigationBarItem(selected = currentTab == "SEARCH", onClick = { currentTab = "SEARCH" }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Kereső") })
                    NavigationBarItem(selected = currentTab == "COLLECTION", onClick = { currentTab = "COLLECTION" }, icon = { Text("❤️", fontSize = 18.sp) }, label = { Text("Gyűjtemény") })
                    NavigationBarItem(selected = currentTab == "PROFILE", onClick = { currentTab = "PROFILE" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Color(0xFF101B15), Color(0xFF05050B))))) {
            when (currentTab) {
                "SEARCH" -> SearchScreen({ playSong(it) }, { scope.launch { repo.add(it); Toast.makeText(context, "Mentve a kedvencekhez!", Toast.LENGTH_SHORT).show() } })
                "COLLECTION" -> CollectionScreen(repo) { playSong(it) }
                "PROFILE" -> ProfileScreen(auth)
            }
        }

        if (isPlayerExpanded && currentlyPlaying != null) {
            ModalBottomSheet(
                onDismissRequest = { isPlayerExpanded = false },
                sheetState = sheetState,
                containerColor = Color(0xFF0D1711),
                modifier = Modifier.fillMaxSize()
            ) {
                FullPlayerScreen(song = currentlyPlaying!!, player = playerViewModel.player, lyrics = lyricsText, onDismiss = { scope.launch { sheetState.hide(); isPlayerExpanded = false } })
            }
        }
    }
}

@Composable
fun SearchScreen(onPlay: (LiveSong) -> Unit, onSave: (LiveSong) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Keresés", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = { 
                query = it
                if (it.length >= 2) {
                    isLoading = true
                    scope.launch { results = searchSpotifyAPI(it); isLoading = false }
                } else results = emptyList()
            },
            modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)),
            placeholder = { Text("Keress dalt vagy előadót...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2822), unfocusedContainerColor = Color(0xFF1E2822), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF16221B)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.coverUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150" }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF1DB954).copy(alpha=0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("🎵", fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text("Teljes Dal", fontSize = 10.sp, color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
            }
        }
        if (onSave != null) { IconButton(onClick = onSave) { Text("❤️", fontSize = 22.sp) } }
    }
}

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

@Composable
fun MiniPlayer(song: LiveSong, player: Player?, isBuffering: Boolean, onClick: () -> Unit) {
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    
    LaunchedEffect(player) { 
        while (true) { 
            isPlaying = player?.isPlaying == true
            delay(500) 
        } 
    }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).shadow(15.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2D24)).clickable { onClick() }) {
        if (isBuffering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, maxLines = 1)
                Text(song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = { 
                if (player?.isPlaying == true) player.pause() else player?.play() 
            }) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun FullPlayerScreen(song: LiveSong, player: Player?, lyrics: String, onDismiss: () -> Unit) {
    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            if (!seeking && player != null) {
                isPlaying = player.isPlaying
                val currentDuration = player.duration
                duration = if (currentDuration > 0) currentDuration.toFloat() else 1f
                position = player.currentPosition.toFloat().coerceAtMost(duration)
            }
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray))
        Spacer(Modifier.height(24.dp))

        AsyncImage(
            model = song.coverUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp)).shadow(15.dp)
        )
        Spacer(Modifier.height(24.dp))

        Text(song.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(song.artist, fontSize = 18.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(24.dp))

        Slider(
            value = position,
            onValueChange = { 
                seeking = true
                position = it 
            },
            onValueChangeFinished = {
                seeking = false
                player?.seekTo(position.toLong())
            },
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toLong()), color = Color.LightGray, fontSize = 12.sp)
            Text(formatTime(duration.toLong()), color = Color.LightGray, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { player?.seekBack() }) {
                Text("⏪", fontSize = 32.sp)
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable {
                    if (player?.isPlaying == true) player.pause() else player?.play()
                },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 28.sp, color = Color.Black)
            }
            Spacer(modifier = Modifier.width(24.dp))
            IconButton(onClick = { player?.seekForward() }) {
                Text("⏩", fontSize = 32.sp)
            }
        }
        
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF16221B)).padding(16.dp)
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = lyrics,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
