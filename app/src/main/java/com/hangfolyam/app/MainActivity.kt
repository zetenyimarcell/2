package com.hangfolyam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.hangfolyam.app.audio.AudioRecorder
import com.hangfolyam.app.data.CollectionRepository
import com.hangfolyam.app.network.RecognitionApi
import com.hangfolyam.app.network.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LiveSong(val id: String, val title: String, val artist: String, val coverUrl: String, val streamUrl: String)

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0D0D19), surface = Color(0xFF1A1A2E), primary = Color(0xFF1DB954))) {
                AppRoot(activity = this, clientId = WEB_CLIENT_ID, auth = auth)
            }
        }
    }
}

@Composable
fun AppRoot(activity: ComponentActivity, clientId: String, auth: FirebaseAuth) {
    var currentScreen by remember { mutableStateOf(if (auth.currentUser != null) "HOME" else "LOGIN") }
    Crossfade(targetState = currentScreen, animationSpec = tween(500), label = "Transition") { screen ->
        when (screen) {
            "LOGIN" -> LoginScreen(clientId, auth, onLoggedIn = { currentScreen = "HOME" }, onPhoneLoginClick = { currentScreen = "PHONE_LOGIN" })
            "PHONE_LOGIN" -> PhoneLoginScreen(activity, auth, onBack = { currentScreen = "LOGIN" }, onSuccess = { currentScreen = "HOME" })
            "HOME" -> HomeScreen()
        }
    }
}

@Composable
fun LoginScreen(clientId: String, auth: FirebaseAuth, onLoggedIn: () -> Unit, onPhoneLoginClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16222A), Color(0xFF3A6073))))) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nova Zene", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text("A világ zenéje. Egy helyen.", fontSize = 16.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                            val result = credentialManager.getCredential(context, request)
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

                            // EZ HIÁNYZOTT EDDIG: valódi Firebase-munkamenet létrehozása
                            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                            auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                                if (task.isSuccessful) onLoggedIn()
                                else Toast.makeText(context, "Firebase hiba: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google hiba: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) { Text("Folytatás Google-fiókkal", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onPhoneLoginClick, modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(25.dp)) {
                Text("📱  Belépés telefonszámmal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun PhoneLoginScreen(activity: ComponentActivity, auth: FirebaseAuth, onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D19)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!codeSent) "Mi a telefonszámod?" else "Írd be az SMS kódot!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = if (!codeSent) phoneNumber else smsCode,
                onValueChange = { if (!codeSent) phoneNumber = it else smsCode = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = { Text(if (!codeSent) "+36301234567 (nemzetközi formátum)" else "000000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        if (!codeSent) {
                            isLoading = true
                            val options = PhoneAuthOptions.newBuilder(auth)
                                .setPhoneNumber(phoneNumber)
                                .setTimeout(60L, TimeUnit.SECONDS)
                                .setActivity(activity)
                                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                        isLoading = false
                                        auth.signInWithCredential(credential).addOnCompleteListener { if (it.isSuccessful) onSuccess() }
                                    }
                                    override fun onVerificationFailed(e: FirebaseException) {
                                        isLoading = false
                                        Toast.makeText(context, "Hiba: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                        isLoading = false
                                        verificationId = id
                                        codeSent = true
                                    }
                                }).build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        } else {
                            verificationId?.let { id ->
                                isLoading = true
                                val credential = PhoneAuthProvider.getCredential(id, smsCode)
                                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) onSuccess()
                                    else Toast.makeText(context, "Hibás kód: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(if (!codeSent) "Kód küldése" else "Belépés") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Vissza", color = Color.Gray) }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val collectionRepo = remember { CollectionRepository() }
    var tab by remember { mutableStateOf("SEARCH") }
    var currentlyPlaying by remember { mutableStateOf<LiveSong?>(null) }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    fun play(song: LiveSong) {
        currentlyPlaying = song
        exoPlayer.setMediaItem(MediaItem.fromUri(song.streamUrl))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentlyPlaying != null) {
                    currentlyPlaying?.let { PlayerBar(it, exoPlayer) }
                }
                NavigationBar(containerColor = Color(0xFF12121A)) {
                    NavigationBarItem(selected = tab == "SEARCH", onClick = { tab = "SEARCH" }, icon = { Text("🔍") }, label = { Text("Kereső") })
                    NavigationBarItem(selected = tab == "RECOGNIZE", onClick = { tab = "RECOGNIZE" }, icon = { Text("🎤") }, label = { Text("Felismerés") })
                    NavigationBarItem(selected = tab == "COLLECTION", onClick = { tab = "COLLECTION" }, icon = { Text("❤") }, label = { Text("Gyűjtemény") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Color(0xFF1E1E30), Color(0xFF0D0D19))))) {
            when (tab) {
                "SEARCH" -> SearchScreen(onPlay = { play(it) }, onSave = { song -> scope.launch { collectionRepo.add(song) } })
                "RECOGNIZE" -> RecognizeScreen(onSave = { song -> scope.launch { collectionRepo.add(song) } })
                "COLLECTION" -> CollectionScreen(repo = collectionRepo, onPlay = { play(it) })
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
        Text("Kereső", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                query = q
                if (q.length >= 2) {
                    isLoading = true
                    scope.launch { results = searchMusicFromInternetFull(q); isLoading = false }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Keress bármit...", color = Color.Gray) },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(results) { song -> SongRow(song, onClick = { onPlay(song) }, onSave = { onSave(song) }) }
            }
        }
    }
}

@Composable
fun RecognizeScreen(onSave: (LiveSong) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RecognitionResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val recorder = remember { AudioRecorder(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) errorText = "Mikrofon engedély szükséges."
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Zenefelismerés", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tényleges felvételt ismer fel (rádió, hangszóró) —\ndúdolást/éneklést nem tud megbízhatóan azonosítani.", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@Button
                }
                result = null; errorText = null; isRecording = true
                scope.launch {
                    val file = recorder.start()
                    delay(6000)
                    recorder.stop()
                    isRecording = false; isProcessing = true
                    val recognized = runCatching { RecognitionApi.recognize(file) }.getOrNull()
                    isProcessing = false
                    if (recognized != null) result = recognized
                    else errorText = "Nem sikerült felismerni. Próbáld hangosabban / közelebbről."
                }
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
        ) { Text(if (isRecording) "⏺" else "🎤", fontSize = 36.sp) }

        Spacer(modifier = Modifier.height(24.dp))
        if (isProcessing) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        errorText?.let { Text(it, color = Color(0xFFFF6B6B)) }
        result?.let { r ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(r.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(r.artist, color = Color.LightGray, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSave(LiveSong(id = r.title + r.artist, title = r.title, artist = r.artist, coverUrl = "", streamUrl = "")) }) {
                Text("Hozzáadás a gyűjteményhez")
            }
        }
    }
}

@Composable
fun CollectionScreen(repo: CollectionRepository, onPlay: (LiveSong) -> Unit) {
    var songs by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { songs = repo.getAll(); loading = false }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Gyűjteményem", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (songs.isEmpty()) {
            Text("Még nincs elmentett szám.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(songs) { song -> SongRow(song, onClick = { onPlay(song) }, onSave = null) }
            }
        }
    }
}

@Composable
fun SongRow(song: LiveSong, onClick: () -> Unit, onSave: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF202030).copy(alpha = 0.7f)).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song.coverUrl.isNotEmpty()) {
            AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)))
        } else {
            Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF33334A)))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
        }
        if (onSave != null) TextButton(onClick = onSave) { Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp) }
    }
}

@Composable
fun PlayerBar(song: LiveSong, exoPlayer: ExoPlayer) {
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    var volume by remember { mutableStateOf(exoPlayer.volume) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(song) {
        while (true) {
            if (!seeking) {
                duration = exoPlayer.duration.coerceAtLeast(1L).toFloat()
                position = exoPlayer.currentPosition.toFloat()
            }
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    Surface(color = Color(0xFF1A1A2E).copy(alpha = 0.97f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.coverUrl.isNotEmpty()) {
                    AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    Text(song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                    Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 20.sp)
                }
            }
            Slider(
                value = position, valueRange = 0f..duration,
                onValueChange = { seeking = true; position = it },
                onValueChangeFinished = { exoPlayer.seekTo(position.toLong()); seeking = false },
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔈", fontSize = 12.sp)
                Slider(
                    value = volume, onValueChange = { volume = it; exoPlayer.volume = it },
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.Gray, activeTrackColor = Color.Gray)
                )
            }
        }
    }
}

suspend fun searchMusicFromInternetFull(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LiveSong>()
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        try {
            val dzConn = URL("https://api.deezer.com/search?q=$encodedQuery&limit=15").openConnection() as HttpURLConnection
            if (dzConn.responseCode == 200) {
                val dzArray = JSONObject(dzConn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("data")
                for (i in 0 until dzArray.length()) {
                    val item = dzArray.getJSONObject(i)
                    val previewUrl = item.optString("preview", "")
                    if (previewUrl.isNotEmpty()) {
                        results.add(LiveSong("dz_" + item.optString("id"), item.optString("title"), item.getJSONObject("artist").optString("name"), item.getJSONObject("album").optString("cover_medium", ""), previewUrl))
                    }
                }
            }
        } catch (e: Exception) { Log.e("Deezer", "Hiba: ${e.message}") }
        try {
            val itConn = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=15&country=HU").openConnection() as HttpURLConnection
            if (itConn.responseCode == 200) {
                val itArray = JSONObject(itConn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("results")
                for (i in 0 until itArray.length()) {
                    val item = itArray.getJSONObject(i)
                    val previewUrl = item.optString("previewUrl", "")
                    val title = item.optString("trackName", "")
                    if (previewUrl.isNotEmpty() && results.none { it.title.equals(title, ignoreCase = true) }) {
                        results.add(LiveSong("it_" + item.optString("trackId"), title, item.optString("artistName"), item.optString("artworkUrl100", "").replace("100x100", "300x300"), previewUrl))
                    }
                }
            }
        } catch (e: Exception) { Log.e("iTunes", "Hiba: ${e.message}") }
    } catch (e: Exception) { Log.e("ZeneKereso", "Fő hiba: ${e.message}") }
    return@withContext results
}
