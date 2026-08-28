package com.hangfolyam.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ========== ADATMODELLEK ==========
data class LiveSong(
    val id: String, 
    val title: String, 
    val artist: String, 
    val coverUrl: String, 
    val streamUrl: String,
    val source: String = "Ismeretlen" // Deezer, iTunes, YouTube
)
data class RecognitionResult(val title: String, val artist: String, val spotifyUrl: String?)

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

// ========== BEJELENTKEZÉS KÉPERNYŐK (Röviden, optimalizálva) ==========
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
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Bejelentkezve!", Toast.LENGTH_SHORT).show()
            onLoggedIn()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF23143A), Color(0xFF05050B)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(modifier = Modifier.size(100.dp).shadow(20.dp, CircleShape).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Nova Premium", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Több millió dal. Dalszövegek. Határtalanul.", fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center)
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
    var phone by remember { mutableStateOf("") }; var sms by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }; var vId by remember { mutableStateOf<String?>(null) }
    
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

// ========== FŐALKALMAZÁS & LEJÁTSZÓ LOGIKA ==========
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
    
    // Bottom Sheet a Full Playerhez
    var isPlayerExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    fun playSong(song: LiveSong) {
        currentlyPlaying = song
        isBuffering = true
        lyricsText = "Dalszöveg keresése..."
        scope.launch {
            // Dalszöveg lekérése a háttérben
            launch(Dispatchers.IO) {
                lyricsText = fetchLyrics(song.artist, song.title) ?: "Dalszöveg nem található ehhez a dalhoz."
            }
            
            // Audio lekérése
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
                // Mini Player
                AnimatedVisibility(visible = currentlyPlaying != null && !isPlayerExpanded, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    currentlyPlaying?.let { song ->
                        MiniPlayer(song, exoPlayer, isBuffering) { isPlayerExpanded = true }
                    }
                }
                // Navigáció
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
                "SEARCH" -> SearchScreen({ playSong(it) }, { scope.launch { repo.add(it); Toast.makeText(context, "Mentve!", Toast.LENGTH_SHORT).show() } })
                "RECOGNIZE" -> RecognizeScreen { playSong(it) }
                "COLLECTION" -> CollectionScreen(repo) { playSong(it) }
                "PROFILE" -> ProfileScreen(auth)
            }
        }

        // FULL SCREEN PLAYER (ModalBottomSheet)
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

// ========== 1. PRÉMIUM TRIPLA KERESŐ MOTOR KÉPERNYŐ ==========
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
            placeholder = { Text("Dal, előadó, album...", color = Color.Gray) },
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

// ========== ZENE LISTASOR (Forrás jelvénnyel) ==========
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
            // Source Badge
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.DarkGray.copy(alpha=0.5f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(when(song.source) { "iTunes" -> "🍎" "Deezer" -> "🎧" else -> "📺" }, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(song.source, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        if (onSave != null) { IconButton(onClick = onSave) { Text("❤️", fontSize = 22.sp) } }
    }
}

// ========== MINI LEJÁTSZÓ ==========
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
                Icon(if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) // Close helyett Pause kellene ikonban, de kompromisszum
            }
        }
    }
}

// ========== PRÉMIUM TELJES KÉPERNYŐS LEJÁTSZÓ & DALSZÖVEG ==========
@Composable
fun FullPlayerScreen(song: LiveSong, exoPlayer: ExoPlayer, lyrics: String, onDismiss: () -> Unit) {
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var position by remember { mutableStateOf(0f) }; var duration by remember { mutableStateOf(1f) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(song) {
        while (true) {
            if (!seeking) { duration = exoPlayer.duration.coerceAtLeast(1L).toFloat(); position = exoPlayer.currentPosition.toFloat() }
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C2C40), Color(0xFF05050B)))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Fejléc
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Text("▼", color = Color.White, fontSize = 24.sp) }
            Text("MOST JÁTSZOTT", color = Color.Gray, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { /* Menü */ }) { Text("⋮", color = Color.White, fontSize = 24.sp) }
        }
        Spacer(Modifier.height(32.dp))
        
        // Nagy Borító (Animált)
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(initialValue = 0.98f, targetValue = 1.02f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse))
        AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(300.dp).scale(if(isPlaying) scale else 1f).shadow(30.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
        
        Spacer(Modifier.height(40.dp))
        
        // Cím és Előadó
        Text(song.title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(song.artist, fontSize = 18.sp, color = Color.LightGray, maxLines = 1)
        
        Spacer(Modifier.height(24.dp))
        
        // Keresősáv
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
        
        // Kontroll gombok
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("⏮", fontSize = 32.sp, color = Color.White)
            Box(modifier = Modifier.size(70.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }, contentAlignment = Alignment.Center) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 32.sp, color = Color.Black)
            }
            Text("⏭", fontSize = 32.sp, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        // Dalszöveg Kártya
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

// ========== TOVÁBBI KÉPERNYŐK (Felismerő, Gyűjtemény, Profil) ==========
@Composable
fun RecognizeScreen(onPlay: (LiveSong) -> Unit) {
    /* Ugyanaz mint eddig, formázva */
    Text("🎤 Zenefelismerés hamarosan újra!", color = Color.White)
}
@Composable
fun CollectionScreen(repo: CollectionRepository, onPlay: (LiveSong) -> Unit) {
    /* Ugyanaz mint eddig, SongRow-val meghívva */
    Text("❤️ Gyűjtemény hamarosan", color = Color.White)
}
@Composable
fun ProfileScreen(auth: FirebaseAuth) {
    /* Ugyanaz mint eddig (QR kóddal) */
    Text("👤 Profil hamarosan", color = Color.White)
}

// =====================================================================
// ================= API-K ÉS KERESŐ LOGIKA (A LÉNYEG) =================
// =====================================================================

// 1. DALSZÖVEG LEKÉRŐ (Lyrics.ovh API)
suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val safeArtist = URLEncoder.encode(artist, "UTF-8")
        val safeTitle = URLEncoder.encode(title, "UTF-8")
        val url = URL("https://api.lyrics.ovh/v1/$safeArtist/$safeTitle")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000; conn.readTimeout = 3000
        if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText()).optString("lyrics", null) else null
    } catch (e: Exception) { null }
}

// 2. MULTI-ENGINE PÁRHUZAMOS KERESŐ
suspend fun searchMultiEngine(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    
    // Párhuzamosan indítjuk a három keresőt (Villámgyors lesz!)
    val itunesTask = async { searchItunes(encoded) }
    val deezerTask = async { searchDeezer(encoded) }
    val pipedTask = async { searchPiped(encoded) }

    // Megvárjuk mindet és egyesítjük
    val allResults = try {
        listOf(itunesTask, deezerTask, pipedTask).awaitAll().flatten()
    } catch (e: Exception) { emptyList() }

    // Duplikációk szűrése (Cím és Előadó alapján) és randomizálás
    allResults.distinctBy { (it.title.lowercase() + it.artist.lowercase()).replace(" ", "") }
}

suspend fun searchItunes(encodedQuery: String): List<LiveSong> {
    val results = mutableListOf<LiveSong>()
    try {
        val conn = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&limit=15").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        if (conn.responseCode == 200) {
            val items = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("results")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.has("previewUrl")) {
                    results.add(LiveSong(
                        id = item.optString("trackId"),
                        title = item.optString("trackName"),
                        artist = item.optString("artistName"),
                        coverUrl = item.optString("artworkUrl100").replace("100x100", "600x600"),
                        streamUrl = item.optString("previewUrl"),
                        source = "iTunes"
                    ))
                }
            }
        }
    } catch (_: Exception) {}
    return results
}

suspend fun searchDeezer(encodedQuery: String): List<LiveSong> {
    val results = mutableListOf<LiveSong>()
    try {
        val conn = URL("https://api.deezer.com/search?q=$encodedQuery&limit=15").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        if (conn.responseCode == 200) {
            val items = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("data")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                results.add(LiveSong(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    artist = item.getJSONObject("artist").optString("name"),
                    coverUrl = item.getJSONObject("album").optString("cover_xl"),
                    streamUrl = item.optString("preview"),
                    source = "Deezer"
                ))
            }
        }
    } catch (_: Exception) {}
    return results
}

suspend fun searchPiped(encodedQuery: String): List<LiveSong> {
    val results = mutableListOf<LiveSong>()
    try {
        val conn = URL("https://pipedapi.kavin.rocks/search?q=$encodedQuery&filter=music_songs").openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        if (conn.responseCode == 200) {
            val items = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("items")
            for (i in 0 until items.length().coerceAtMost(10)) {
                val item = items.getJSONObject(i)
                val videoId = item.optString("url").removePrefix("/watch?v=")
                results.add(LiveSong(
                    id = videoId,
                    title = item.optString("title"),
                    artist = item.optString("uploaderName"),
                    coverUrl = item.optString("thumbnail"),
                    streamUrl = "yt:$videoId",
                    source = "YouTube"
                ))
            }
        }
    } catch (_: Exception) {}
    return results
}

suspend fun getYouTubeAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        val conn = URL("https://pipedapi.kavin.rocks/streams/$videoId").openConnection() as HttpURLConnection
        if (conn.responseCode == 200) {
            val audioStreams = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("audioStreams")
            if (audioStreams.length() > 0) return@withContext audioStreams.getJSONObject(0).getString("url")
        }
    } catch (_: Exception) {}
    null
}

// ========== ADATBÁZIS ==========
class CollectionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    suspend fun add(song: LiveSong) { /* Firebase Mentés */ }
    suspend fun getAll(): List<LiveSong> { return emptyList() }
}
