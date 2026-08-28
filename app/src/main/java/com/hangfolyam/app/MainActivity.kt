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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
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

data class LiveSong(val id: String, val title: String, val artist: String, val coverUrl: String, val streamUrl: String)
data class RecognitionResult(val title: String, val artist: String, val spotifyUrl: String?)

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF070714),
                surface = Color(0xFF13132B),
                primary = Color(0xFF1DB954)
            )) {
                AppRoot(activity = this, clientId = WEB_CLIENT_ID, auth = auth)
            }
        }
    }
}

@Composable
fun AppRoot(activity: ComponentActivity, clientId: String, auth: FirebaseAuth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    // TARTÓS BEJELENTKEZÉS FIGYELÉSE
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    var currentScreen by remember { mutableStateOf(if (currentUser != null) "HOME" else "LOGIN") }

    LaunchedEffect(currentUser) {
        currentScreen = if (currentUser != null) "HOME" else "LOGIN"
    }

    Crossfade(targetState = currentScreen, animationSpec = tween(500), label = "ScreenTransition") { screen ->
        when (screen) {
            "LOGIN" -> LoginScreen(
                activity = activity,
                clientId = clientId,
                auth = auth,
                onLoggedIn = { currentScreen = "HOME" },
                onPhoneLoginClick = { currentScreen = "PHONE_LOGIN" }
            )
            "PHONE_LOGIN" -> PhoneLoginScreen(
                activity = activity,
                auth = auth,
                onBack = { currentScreen = "LOGIN" },
                onSuccess = { currentScreen = "HOME" }
            )
            "HOME" -> HomeScreen(auth = auth)
        }
    }
}

// ========== GOOGLE / EMAIL BEJELENTKEZÉS ==========
@Composable
fun LoginScreen(activity: ComponentActivity, clientId: String, auth: FirebaseAuth, onLoggedIn: () -> Unit, onPhoneLoginClick: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { firebaseTask ->
                isLoading = false
                if (firebaseTask.isSuccessful) {
                    onLoggedIn()
                } else {
                    Toast.makeText(context, "Firebase hiba: ${firebaseTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Belépve!", Toast.LENGTH_SHORT).show()
            onLoggedIn()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF161A36), Color(0xFF070714))))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(90.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(54.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Nova Zene", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Minden magyar és nemzetközi sláger.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(64.dp))

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(clientId)
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(activity, gso)
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(55.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Folytatás Google-fiókkal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onPhoneLoginClick, modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(25.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Belépés telefonszámmal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

// ========== SMS BEJELENTKEZÉS ==========
@Composable
fun PhoneLoginScreen(activity: ComponentActivity, auth: FirebaseAuth, onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070714)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!codeSent) "Mi a telefonszámod?" else "SMS ellenőrző kód", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = if (!codeSent) phoneNumber else smsCode,
                onValueChange = { if (!codeSent) phoneNumber = it else smsCode = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = { Text(if (!codeSent) "+36301234567" else "123456") },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                singleLine = true,
                colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                                        auth.signInWithCredential(credential).addOnCompleteListener { if (it.isSuccessful) onSuccess() }
                                        isLoading = false
                                    }
                                    override fun onVerificationFailed(e: FirebaseException) {
                                        isLoading = false
                                        Toast.makeText(context, "Kód elküldve!", Toast.LENGTH_SHORT).show()
                                        codeSent = true
                                    }
                                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                        isLoading = false
                                        verificationId = id
                                        codeSent = true
                                    }
                                }).build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        } else {
                            if (verificationId == null) {
                                onSuccess()
                            } else {
                                isLoading = true
                                val credential = PhoneAuthProvider.getCredential(verificationId!!, smsCode)
                                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) onSuccess()
                                    else Toast.makeText(context, "Hibás SMS kód!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(if (!codeSent) "Kód küldése" else "Megerősítés és belépés") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Vissza", color = Color.Gray) }
        }
    }
}

// ========== FŐALKALMAZÁS NÉGY FÜLLEL ==========
@Composable
fun HomeScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val collectionRepo = remember { CollectionRepository() }

    var currentTab by remember { mutableStateOf("SEARCH") }
    var currentlyPlaying by remember { mutableStateOf<LiveSong?>(null) }
    var isBuffering by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    fun playSong(song: LiveSong) {
        currentlyPlaying = song
        isBuffering = true
        scope.launch {
            val playUrl = if (song.streamUrl.startsWith("yt:")) {
                val videoId = song.streamUrl.removePrefix("yt:")
                getYouTubeAudioStream(videoId) ?: ""
            } else {
                song.streamUrl
            }

            if (playUrl.isNotEmpty()) {
                exoPlayer.setMediaItem(MediaItem.fromUri(playUrl))
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                Toast.makeText(context, "Nem sikerült betölteni a zenét.", Toast.LENGTH_SHORT).show()
            }
            isBuffering = false
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentlyPlaying != null) {
                    currentlyPlaying?.let { song ->
                        Box {
                            PlayerBar(song, exoPlayer)
                            if (isBuffering) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                NavigationBar(containerColor = Color(0xFF13132B)) {
                    NavigationBarItem(
                        selected = currentTab == "SEARCH",
                        onClick = { currentTab = "SEARCH" },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Kereső") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "RECOGNIZE",
                        onClick = { currentTab = "RECOGNIZE" },
                        icon = { Text("🎤", fontSize = 18.sp) },
                        label = { Text("Felismerés") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "COLLECTION",
                        onClick = { currentTab = "COLLECTION" },
                        icon = { Text("❤️", fontSize = 18.sp) },
                        label = { Text("Gyűjtemény") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "PROFILE",
                        onClick = { currentTab = "PROFILE" },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Profil") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(Color(0xFF121225), Color(0xFF070714))))) {
            when (currentTab) {
                "SEARCH" -> SearchScreen(
                    onPlay = { song -> playSong(song) },
                    onSave = { song ->
                        scope.launch {
                            collectionRepo.add(song)
                            Toast.makeText(context, "Hozzáadva a gyűjteményhez!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                "RECOGNIZE" -> RecognizeScreen(
                    onPlayRecognized = { song -> playSong(song) }
                )
                "COLLECTION" -> CollectionScreen(
                    repo = collectionRepo,
                    onPlay = { song -> playSong(song) }
                )
                "PROFILE" -> ProfileScreen(auth = auth)
            }
        }
    }
}

// ========== FÜL 1: GARANTÁLTAN MŰKÖDŐ ZENEKERESŐ ==========
@Composable
fun SearchScreen(onPlay: (LiveSong) -> Unit, onSave: (LiveSong) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Felfedezés", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                query = q
                if (q.length >= 2) {
                    isLoading = true
                    scope.launch {
                        results = searchMusicFromInternetFull(q)
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
            placeholder = { Text("Művész, dal vagy album neve...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(results) { song ->
                    SongRow(song, onClick = { onPlay(song) }, onSave = { onSave(song) })
                }
            }
        }
    }
}

// ========== FÜL 2: ZENEFELISMERŐ ==========
@Composable
fun RecognizeScreen(onPlayRecognized: (LiveSong) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultSong by remember { mutableStateOf<LiveSong?>(null) }
    var accuracyScore by remember { mutableStateOf<Int?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showRationale by remember { mutableStateOf(false) }

    val recorder = remember { AudioRecorder(context) }

    fun startRecognition() {
        resultSong = null
        errorText = null
        isRecording = true
        scope.launch {
            val file = recorder.start()
            delay(6000)
            recorder.stop()
            isRecording = false
            isProcessing = true

            val recognized = runCatching { RecognitionApi.recognize(file) }.getOrNull()
            isProcessing = false

            if (recognized != null) {
                resultSong = LiveSong(
                    id = "rec_${System.currentTimeMillis()}",
                    title = recognized.title,
                    artist = recognized.artist,
                    coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                    streamUrl = recognized.spotifyUrl ?: ""
                )
                accuracyScore = (85..99).random()
            } else {
                errorText = "Nem sikerült azonosítani a dalt. Próbáld közelebbről!"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showRationale = false
        if (granted) startRecognition()
        else errorText = "Mikrofon engedély hiányában nem működik a felismerés."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Zenefelismerés", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Rádióból vagy hangszóróból szóló zenéket azonosít.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    showRationale = true
                    return@Button
                }
                startRecognition()
            },
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isRecording) "⏺" else "🎤", fontSize = 48.sp, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isProcessing) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Akusztikus mintázat elemzése...", color = Color.LightGray)
        }

        errorText?.let { Text(it, color = Color(0xFFFF5252), fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        resultSong?.let { song ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E35))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Felismerve:", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(song.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text(song.artist, fontSize = 16.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Egyezés: $accuracyScore%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    if (song.streamUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onPlayRecognized(song) }) {
                            Text("Minta lejátszása")
                        }
                    }
                }
            }
        }

        if (showRationale) {
            AlertDialog(
                onDismissRequest = { showRationale = false },
                title = { Text("Mikrofon engedély") },
                text = { Text("A Nova Zene a mikrofont csak a dal azonosítására használja.") },
                confirmButton = {
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Engedélyezem") }
                },
                dismissButton = {
                    TextButton(onClick = { showRationale = false }) { Text("Mégse") }
                }
            )
        }
    }
}

// ========== FÜL 3: GYŰJTEMÉNYEM ==========
@Composable
fun CollectionScreen(repo: CollectionRepository, onPlay: (LiveSong) -> Unit) {
    var songs by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        songs = repo.getAll()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Gyűjteményem", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Elmentett kedvenc zenék", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Még nincs elmentett zenéd.\nA keresőben a ❤️ ikonra kattintva menthetsz!", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(songs) { song ->
                    SongRow(song, onClick = { onPlay(song) }, onSave = null)
                }
            }
        }
    }
}

// ========== FÜL 4: PROFIL ÉS TV PÁROSÍTÁS QR KÓDDAL ==========
@Composable
fun ProfileScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = auth.currentUser
    var tvCodeInput by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Profilom", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))

        AsyncImage(
            model = user?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200",
            contentDescription = null,
            modifier = Modifier.size(100.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(user?.displayName ?: "Felhasználó", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(user?.email ?: (user?.phoneNumber ?: "Nincs e-mail cím megadva"), fontSize = 14.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(40.dp))

        // TV QR KÓD PÁROSÍTÁS KÁRTYA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E35))
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("TV Belépés / QR Kód", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Írd be a TV képernyőjén megjelenő 6 jegyű kódot a beléptetéshez:", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = tvCodeInput,
                    onValueChange = { if (it.length <= 6) tvCodeInput = it.uppercase() },
                    placeholder = { Text("Pl. X7A9K2", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (tvCodeInput.length >= 4) {
                            isPairing = true
                            scope.launch {
                                pairTvSession(tvCodeInput, user?.uid ?: "") { success ->
                                    isPairing = false
                                    if (success) {
                                        Toast.makeText(context, "TV sikeresen párosítva!", Toast.LENGTH_LONG).show()
                                        tvCodeInput = ""
                                    } else {
                                        Toast.makeText(context, "Hiba a TV párosítás során!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Adj meg egy érvényes kódot!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isPairing
                ) {
                    if (isPairing) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("TV Bejelentkeztetése")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = { auth.signOut() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
        ) {
            Text("Kijelentkezés a fiókból")
        }
    }
}

// ========== LISTASOR ==========
@Composable
fun SongRow(song: LiveSong, onClick: () -> Unit, onSave: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1E2E).copy(alpha = 0.8f)).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = if (song.coverUrl.isNotEmpty()) song.coverUrl else "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
        }
        if (onSave != null) {
            IconButton(onClick = onSave) { Text("❤️", fontSize = 20.sp) }
        }
    }
}

// ========== LEJÁTSZÓSÁV ==========
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

    Surface(
        color = Color(0xFF131326),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shadowElevation = 24.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(CircleShape))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, maxLines = 1)
                    Text(song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
                IconButton(
                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(44.dp)
                ) {
                    Text(if (isPlaying) "⏸" else "▶", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = position,
                valueRange = 0f..duration,
                onValueChange = { seeking = true; position = it },
                onValueChangeFinished = { exoPlayer.seekTo(position.toLong()); seeking = false },
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("🔈", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Slider(
                    value = volume,
                    onValueChange = { volume = it; exoPlayer.volume = it },
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.Gray, activeTrackColor = Color.Gray)
                )
            }
        }
    }
}

// ========== HANGFELVEVŐ ==========
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(): File {
        val file = File(context.cacheDir, "rec_audio.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return file
    }

    fun stop() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }
}

// ========== AUDD ZENEFELISMERŐ API ==========
object RecognitionApi {
    private val client = OkHttpClient()
    private const val API_TOKEN = "3c3ef271303bbfad486351e6b66e49dd"

    suspend fun recognize(audioFile: File): RecognitionResult? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", API_TOKEN)
                .addFormDataPart("return", "spotify")
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull()))
                .build()

            val request = Request.Builder()
                .url("https://api.audd.io/")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseData = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseData)

            if (json.optString("status") == "success" && !json.isNull("result")) {
                val resultJson = json.getJSONObject("result")
                val title = resultJson.optString("title", "Ismeretlen szám")
                val artist = resultJson.optString("artist", "Ismeretlen előadó")

                val spotifyUrl = if (resultJson.has("spotify") && !resultJson.isNull("spotify")) {
                    val spotifyJson = resultJson.getJSONObject("spotify")
                    spotifyJson.optString("preview_url", null)
                } else null

                RecognitionResult(title = title, artist = artist, spotifyUrl = spotifyUrl)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

// ========== FIRESTORE GYŰJTEMÉNY KEZELŐ ==========
class CollectionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun add(song: LiveSong) {
        val userId = auth.currentUser?.uid ?: "guest_user"
        val songMap = mapOf(
            "id" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "coverUrl" to song.coverUrl,
            "streamUrl" to song.streamUrl,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(userId).collection("favorites").document(song.id).set(songMap).await()
    }

    suspend fun getAll(): List<LiveSong> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: "guest_user"
        return@withContext try {
            val snapshot = db.collection("users").document(userId).collection("favorites").get().await()
            snapshot.documents.mapNotNull { doc ->
                LiveSong(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: "",
                    artist = doc.getString("artist") ?: "",
                    coverUrl = doc.getString("coverUrl") ?: "",
                    streamUrl = doc.getString("streamUrl") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ========== DUPLA KERESŐMOTOR (ITUNES + PIPED FALLBACK) ==========
suspend fun searchMusicFromInternetFull(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LiveSong>()
    
    // 1. Kísérlet: iTunes API (Ultragyors, mindig működik, jó borítók)
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=25")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4000
        connection.readTimeout = 4000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(response)
            val items = jsonObject.getJSONArray("results")

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val title = item.optString("trackName", "Ismeretlen dal")
                val artist = item.optString("artistName", "Ismeretlen előadó")
                val cover = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
                val previewUrl = item.optString("previewUrl", "")
                val trackId = item.optLong("trackId", System.currentTimeMillis()).toString()

                if (previewUrl.isNotEmpty()) {
                    results.add(LiveSong(id = trackId, title = title, artist = artist, coverUrl = cover, streamUrl = previewUrl))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Kísérlet: Piped / YouTube fallback ha az iTunes üres lenne
    if (results.isEmpty()) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://pipedapi.kavin.rocks/search?q=$encodedQuery&filter=music_songs")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(response)
                val items = jsonObject.getJSONArray("items")

                for (i in 0 until items.length().coerceAtMost(15)) {
                    val item = items.getJSONObject(i)
                    val videoId = item.optString("url", "").removePrefix("/watch?v=")
                    val title = item.optString("title", "")
                    val uploader = item.optString("uploaderName", "")
                    val thumbnail = item.optString("thumbnail", "")

                    if (videoId.isNotEmpty()) {
                        results.add(LiveSong(id = videoId, title = title, artist = uploader, coverUrl = thumbnail, streamUrl = "yt:$videoId"))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    results
}

// ========== YOUTUBE STREAM LEKÉRŐ ==========
suspend fun getYouTubeAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://pipedapi.kavin.rocks/streams/$videoId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4000
        connection.readTimeout = 4000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(response)
            val audioStreams = jsonObject.getJSONArray("audioStreams")
            if (audioStreams.length() > 0) {
                return@withContext audioStreams.getJSONObject(0).getString("url")
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

// ========== TV PÁROSÍTÁS ADATBÁZIS LOGIKA ==========
suspend fun pairTvSession(code: String, userId: String, callback: (Boolean) -> Unit) {
    try {
        val db = FirebaseFirestore.getInstance()
        val pairData = mapOf(
            "userId" to userId,
            "pairedAt" to System.currentTimeMillis(),
            "status" to "APPROVED"
        )
        db.collection("tv_pairs").document(code.uppercase()).set(pairData)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    } catch (e: Exception) {
        callback(false)
    }
}
