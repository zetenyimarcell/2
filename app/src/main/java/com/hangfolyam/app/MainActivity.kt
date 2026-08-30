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
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import java.util.regex.Pattern
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin

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
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F101A), surface = Color(0xFF1A1C29))) {
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
    var showRegistration by remember { mutableStateOf(false) }

    if (currentUser == null) {
        if (showRegistration) {
            VaultRegistrationScreen(
                onBack = { showRegistration = false },
                onRegisterSuccess = { currentUser = auth.currentUser }
            )
        } else {
            LoginScreen(
                onLoginSuccess = { currentUser = auth.currentUser },
                onNavigateToRegister = { showRegistration = true }
            )
        }
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
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
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
                    auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onLoginSuccess() }
                        .addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba" }
                } else {
                    errorMessage = "Kérjük töltsd ki a mezőket!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Bejelentkezés") }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateToRegister) {
            Text("Nincs fiókod? Regisztráció (Vault)")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
        
        OutlinedButton(
            onClick = {
                if (activity != null) {
                    try {
                        val provider = OAuthProvider.newBuilder("google.com").build()
                        auth.startActivityForSignInWithProvider(activity, provider)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = "Google bejelentkezés sikertelen: ${it.localizedMessage}" }
                    } catch (e: Exception) {
                        errorMessage = "Hiba: ${e.localizedMessage}"
                    }
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
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun VaultRegistrationScreen(onBack: () -> Unit, onRegisterSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()

    var entropy by remember { mutableStateOf(0.0) }
    LaunchedEffect(password) {
        if (password.isEmpty()) {
            entropy = 0.0
        } else {
            var pool = 0
            if (password.any { it.isLowerCase() }) pool += 26
            if (password.any { it.isUpperCase() }) pool += 26
            if (password.any { it.isDigit() }) pool += 10
            if (password.any { !it.isLetterOrDigit() }) pool += 32
            entropy = password.length * log2(pool.toDouble())
        }
    }

    val tier = when {
        entropy < 1 -> 0  
        entropy < 30 -> 1 
        entropy < 50 -> 2 
        entropy < 70 -> 3 
        else -> 4         
    }

    val tierName = when (tier) {
        0 -> "No lock at all"
        1 -> "A bent paperclip"
        2 -> "A padlock"
        3 -> "A deadbolt"
        else -> "A bank vault"
    }
    
    val tierDesc = when {
        entropy < 1 -> "The door is standing open."
        entropy < 20 -> "Cracked instantly."
        entropy < 30 -> "Cracked in under a second."
        entropy < 40 -> "Cracked in minutes."
        entropy < 50 -> "Cracked in days."
        entropy < 60 -> "Cracked in months."
        entropy < 70 -> "Cracked in years."
        else -> "Cracked in thousand years."
    }

    val tierColor = when (tier) {
        0 -> Color(0xFF555555) 
        1 -> Color(0xFFE57373) 
        2 -> Color(0xFFFFB74D) 
        3 -> Color(0xFFFFD54F) 
        else -> Color(0xFF4DB6AC) 
    }

    val animatedColor by animateColorAsState(targetValue = tierColor, animationSpec = tween(500))

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F101A)).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Vissza", tint = Color.Gray) }
        }
        
        Text("VAULT REGISTRATION", color = animatedColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6B4EE6), unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Gray) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, tint = Color.Gray, contentDescription = null)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = animatedColor, unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth().border(1.dp, animatedColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).background(Color(0xFF1A1C29), RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF252836)),
                    contentAlignment = Alignment.Center
                ) {
                    when (tier) {
                        0 -> Icon(Icons.Default.DoorSliding, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        1 -> Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(40.dp))
                        2 -> Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(40.dp))
                        3 -> DeadboltIcon()
                        else -> BankVaultIcon(entropy) // Átadott entrópia az animációhoz
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(4) { index ->
                            val isActive = tier >= (index + 1)
                            val barColor by animateColorAsState(if (isActive) tierColor else Color(0xFF252836), tween(400))
                            Box(modifier = Modifier.weight(1f).height(4.dp).padding(horizontal = 2.dp).clip(CircleShape).background(barColor))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tierName, color = animatedColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(tierDesc, color = Color.LightGray, fontSize = 14.sp)
                    Text("${entropy.toInt()} bits of entropy", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { onRegisterSuccess() }
                        .addOnFailureListener { errorMessage = it.localizedMessage ?: "Hiba történt" }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (tier >= 3) Color(0xFF6B4EE6) else Color.DarkGray)
        ) {
            Text("Fiók létrehozása", fontSize = 16.sp)
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = Color.Red, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun DeadboltIcon() {
    Canvas(modifier = Modifier.size(40.dp)) {
        drawRoundRect(color = Color.LightGray, topLeft = Offset(0f, 10f), size = Size(20f, 20f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(color = Color.DarkGray, topLeft = Offset(22f, 0f), size = Size(18f, 40f), cornerRadius = CornerRadius(2f, 2f))
        drawRect(color = Color.Black, topLeft = Offset(10f, 18f), size = Size(14f, 4f))
    }
}

@Composable
fun BankVaultIcon(entropy: Double) {
    // Forgó animáció a tárcsához
    val rotationAngle by animateFloatAsState(
        targetValue = (entropy * 18).toFloat(), // Gépelésre gyorsan forog
        animationSpec = tween(600, easing = FastOutSlowInEasing)
    )
    
    Canvas(modifier = Modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        drawCircle(color = Color.DarkGray, radius = radius, center = center)
        drawCircle(color = Color.Gray, radius = radius * 0.8f, center = center, style = Stroke(width = 4f))
        drawCircle(color = Color(0xFF4DB6AC), radius = radius * 0.2f, center = center)
        
        rotate(rotationAngle, center) {
            for (i in 0 until 6) {
                val angle = i * (Math.PI / 3).toFloat()
                drawLine(
                    color = Color.LightGray,
                    start = center,
                    end = Offset(center.x + radius * 0.7f * cos(angle), center.y + radius * 0.7f * sin(angle)),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Üdvözöllek a Hangfolyam-ban!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Keresd meg a zenét, vagy ismerd fel a beépített AI-val!", color = Color.Gray)
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
        currentLyrics = "Hanganyag felkutatása Invidious/Piped szervereken..."
        exoPlayer?.stop()
        
        coroutineScope.launch {
            var playUrl = ""
            
            // 1. Esély: Invidious API szerverek (sokkal stabilabbak zene kinyerésre)
            val invidiousInstances = listOf("invidious.jing.rocks", "inv.tux.pizza", "invidious.nerdvpn.de", "invidious.privacydev.net")
            for (instance in invidiousInstances) {
                try {
                    val req = Request.Builder().url("https://$instance/api/v1/videos/${song.audioUrl}").build()
                    val res = withContext(Dispatchers.IO) { sharedHttpClient.newCall(req).execute() }
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string() ?: "")
                        val formats = json.optJSONArray("adaptiveFormats")
                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                val format = formats.getJSONObject(i)
                                if (format.optString("type").startsWith("audio")) {
                                    playUrl = format.optString("url")
                                    break
                                }
                            }
                        }
                    }
                    if (playUrl.isNotEmpty()) break
                } catch (e: Exception) { continue }
            }

            // 2. Esély: Piped API ha az Invidious is cserben hagy
            if (playUrl.isEmpty()) {
                val pipedInstances = listOf("pipedapi.kavin.rocks", "pipedapi.smnz.de", "api.piped.projectsegfau.lt")
                for (instance in pipedInstances) {
                    try {
                        val req = Request.Builder().url("https://$instance/streams/${song.audioUrl}").build()
                        val res = withContext(Dispatchers.IO) { sharedHttpClient.newCall(req).execute() }
                        if (res.isSuccessful) {
                            val streams = JSONObject(res.body?.string() ?: "").optJSONArray("audioStreams")
                            if (streams != null && streams.length() > 0) {
                                playUrl = streams.getJSONObject(0).optString("url")
                                break
                            }
                        }
                    } catch (e: Exception) { continue }
                }
            }
            
            if (playUrl.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    currentLyrics = "Dalszöveg betöltése..."
                }
            } else {
                currentLyrics = "Hiba: Sajnos egyetlen független streamszerver sem tudta lekérni ezt a dalt. Próbálj rákeresni egy másik verzióra."
            }

            val lyrics = fetchLyrics(song.artist, song.title)
            if (currentLyrics != "Hiba: Sajnos egyetlen független streamszerver sem tudta lekérni ezt a dalt. Próbálj rákeresni egy másik verzióra.") {
                currentLyrics = lyrics ?: "Nincs elérhető dalszöveg ehhez a dalhoz."
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés (pl. Azahriah - 3 korty)...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        aiStatus = "Keresés a YouTube-on..."
                        coroutineScope.launch {
                            val optimizedQuery = optimizeSearchWithGemini(query)
                            searchResults = searchYouTubeDirectly(optimizedQuery)
                            
                            if (searchResults.isEmpty()) {
                                searchResults = searchYouTubePiped(optimizedQuery)
                            }
                            
                            aiStatus = if (searchResults.isEmpty()) "Nincs találat." else null
                            isSearching = false
                        }
                    }) { Icon(Icons.Default.Search, contentDescription = "Keresés") }
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
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults.indices.toList()) { index ->
                    val song = searchResults[index]
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
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

suspend fun searchYouTubeDirectly(query: String): List<Song> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        val response = sharedHttpClient.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext emptyList()
        val pattern = Pattern.compile("var ytInitialData = (\\{.*?\\});</script>")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val json = JSONObject(matcher.group(1))
            val contents = json.optJSONObject("contents")?.optJSONObject("twoColumnSearchResultsRenderer")?.optJSONObject("primaryContents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
            val list = mutableListOf<Song>()
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val videoRenderer = contents.optJSONObject(i)?.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.optString("videoId")
                        val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Ismeretlen"
                        val owner = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Ismeretlen"
                        if (videoId.isNotEmpty()) list.add(Song(title, owner, videoId))
                    }
                }
            }
            return@withContext list
        }
    } catch (e: Exception) {}
    return@withContext emptyList()
}

suspend fun searchYouTubePiped(query: String): List<Song> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder().url("https://pipedapi.kavin.rocks/search?q=$encodedQuery").build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val jsonObject = JSONObject(response.body?.string() ?: "")
            if (jsonObject.has("items")) {
                val jsonArray = jsonObject.getJSONArray("items")
                val list = mutableListOf<Song>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.optString("type") == "stream") {
                        list.add(Song(item.getString("title"), item.optString("uploaderName", "Ismeretlen"), item.getString("url").replace("/watch?v=", "")))
                    }
                }
                return@withContext list
            }
        }
    } catch (e: Exception) {}
    return@withContext emptyList()
}

suspend fun optimizeSearchWithGemini(userQuery: String): String = withContext(Dispatchers.IO) {
    try {
        val jsonBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", "Készíts ebből tiszta YouTube keresőkifejezést (csak előadó és cím): '$userQuery'")))))
        }
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                return@withContext candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text")?.trim() ?: userQuery
            }
        }
    } catch (e: Exception) {}
    return@withContext userQuery
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val cleanArtist = java.net.URLEncoder.encode(artist.take(20), "UTF-8")
        val cleanTitle = java.net.URLEncoder.encode(title.take(30).replace(Regex("\\(.*\\)"), "").trim(), "UTF-8")
        val request = Request.Builder().url("https://api.lyrics.ovh/v1/$cleanArtist/$cleanTitle").header("User-Agent", "Mozilla/5.0").build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) return@withContext JSONObject(response.body?.string() ?: "").optString("lyrics")
    } catch (_: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Koppints a mikrofonra a felismeréshez") }
    val coroutineScope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) status = "Mikrofon engedély megtagadva!" }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(120.dp).background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape).clickable {
                val permCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                if (permCheck == PackageManager.PERMISSION_GRANTED) {
                    if (!isListening) {
                        isListening = true
                        status = "Hang rögzítése tisztább minőségben (7 másodperc)..."
                        coroutineScope.launch {
                            val audioFile = File(context.cacheDir, "record.mp4")
                            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                            try {
                                // Megemelt minőség a pontosabb AI felismeréshez
                                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                recorder.setAudioEncodingBitRate(128000)
                                recorder.setAudioSamplingRate(44100) 
                                recorder.setOutputFile(audioFile.absolutePath)
                                recorder.prepare()
                                recorder.start()
                                
                                delay(7000) // Hosszabb felvétel az analizáláshoz
                                
                                recorder.stop()
                                recorder.release()
                                
                                status = "AudD zenefelismerő indítása..."
                                var queryStr = recognizeWithAudD(audioFile)
                                
                                if (queryStr == null) {
                                    status = "AudD nem találta, átadás a Gemini AI-nak..."
                                    queryStr = recognizeWithGeminiFallback(audioFile)
                                }
                                
                                if (queryStr != null) {
                                    status = "Megvan: $queryStr. Betöltés a lejátszóba..."
                                    val songs = searchYouTubeDirectly(queryStr)
                                    if (songs.isNotEmpty()) {
                                        // Átváltunk a Kereső fül logikájához Invidious-al
                                        var playUrl = ""
                                        val instances = listOf("invidious.jing.rocks", "inv.tux.pizza")
                                        for (instance in instances) {
                                            try {
                                                val req = Request.Builder().url("https://$instance/api/v1/videos/${songs[0].audioUrl}").build()
                                                val res = withContext(Dispatchers.IO) { sharedHttpClient.newCall(req).execute() }
                                                if (res.isSuccessful) {
                                                    val format = JSONObject(res.body?.string() ?: "").optJSONArray("adaptiveFormats")?.getJSONObject(0)
                                                    if (format != null) {
                                                        playUrl = format.optString("url")
                                                        break
                                                    }
                                                }
                                            } catch (e: Exception) {}
                                        }
                                        
                                        if (playUrl.isNotEmpty()) {
                                            status = "Lejátszás: ${songs[0].title}"
                                            withContext(Dispatchers.Main) {
                                                exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                                                exoPlayer?.prepare()
                                                exoPlayer?.play()
                                            }
                                        } else {
                                            status = "Találat megvan ($queryStr), de a stream nem indul."
                                        }
                                    } else {
                                        status = "A YouTube-on sem található."
                                    }
                                } else {
                                    status = "Sajnos túl nagy a háttérzaj, egyik AI sem ismerte fel."
                                }
                            } catch (e: Exception) {
                                status = "Hiba történt a felvételkor: ${e.message}"
                                try { recorder.release() } catch (ex: Exception) {}
                            } finally {
                                isListening = false
                                if (audioFile.exists()) audioFile.delete()
                            }
                        }
                    }
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp)) }
        Spacer(modifier = Modifier.height(24.dp))
        Text(status, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
    }
}

suspend fun recognizeWithAudD(file: File): String? = withContext(Dispatchers.IO) {
    try {
        val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("api_token", "test").addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaTypeOrNull())).build()
        val req = Request.Builder().url("https://api.audd.io/").post(reqBody).build()
        val res = sharedHttpClient.newCall(req).execute()
        if (res.isSuccessful) {
            val json = JSONObject(res.body?.string() ?: "")
            if (json.optString("status") == "success" && !json.isNull("result")) {
                val result = json.getJSONObject("result")
                return@withContext "${result.optString("artist")} ${result.optString("title")}"
            }
        }
    } catch (e: Exception) {}
    return@withContext null
}

suspend fun recognizeWithGeminiFallback(file: File): String? = withContext(Dispatchers.IO) {
    try {
        val base64Audio = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val jsonBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().apply {
                put(JSONObject().put("text", "Elemezd ezt a hangfelvételt. Ismerd fel a benne hallható dalt (előadó - cím). Ha csak dalszöveget hallasz, írd le a szöveget. Csak a keresendő kifejezést add vissza, semmi mást!"))
                put(JSONObject().put("inlineData", JSONObject().apply { put("mimeType", "audio/mp4"); put("data", base64Audio) }))
            })))
        }
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY").post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
        val res = sharedHttpClient.newCall(req).execute()
        if (res.isSuccessful) {
            val json = JSONObject(res.body?.string() ?: "")
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                return@withContext candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text")?.trim()
            }
        }
    } catch (e: Exception) {}
    return@withContext null
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSignOut) { Text("Kijelentkezés") }
    }
}
