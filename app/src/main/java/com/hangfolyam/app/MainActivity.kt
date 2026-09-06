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
import androidx.compose.animation.animateColorAsState
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
import okhttp3.OkHttpClient
import okhttp3.Request
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

    val entropy = remember(password) {
        if (password.isEmpty()) {
            0.0
        } else {
            var pool = 0
            if (password.any { it.isLowerCase() }) pool += 26
            if (password.any { it.isUpperCase() }) pool += 26
            if (password.any { it.isDigit() }) pool += 10
            if (password.any { !it.isLetterOrDigit() }) pool += 32
            if (pool == 0) 0.0 else password.length * log2(pool.toDouble())
        }
    }

    val tier = when {
        entropy < 1 -> 0
        entropy < 25 -> 1
        entropy < 45 -> 2
        entropy < 65 -> 3
        else -> 4
    }

    val tierName = when (tier) {
        0 -> "No lock at all"
        1 -> "A bent paperclip"
        2 -> "A padlock"
        3 -> "A deadbolt"
        else -> "A bank vault"
    }
    
    val tierDesc = when (tier) {
        0 -> "The door is standing open."
        1 -> "Cracked in under a second."
        2 -> "Cracked in minutes or hours."
        3 -> "Cracked in years."
        else -> "Cracked in thousands of years."
    }

    val tierColor = when (tier) {
        0 -> Color(0xFF555555) 
        1 -> Color(0xFFE57373) 
        2 -> Color(0xFFFFB74D) 
        3 -> Color(0xFFFFD54F) 
        else -> Color(0xFF4DB6AC) 
    }

    val animatedColor by animateColorAsState(targetValue = tierColor, animationSpec = tween(500), label = "colorAnimation")

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
                        else -> BankVaultIcon()
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(4) { index ->
                            val isActive = tier >= (index + 1)
                            val barColor by animateColorAsState(if (isActive) tierColor else Color(0xFF252836), tween(400), label = "barColor")
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
            colors = ButtonDefaults.buttonColors(containerColor = if (tier >= 2) Color(0xFF6B4EE6) else Color.DarkGray)
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
        drawRoundRect(
            color = Color.LightGray,
            topLeft = Offset(0f, 10f),
            size = Size(20f, 20f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color.DarkGray,
            topLeft = Offset(22f, 0f),
            size = Size(18f, 40f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(10f, 18f),
            size = Size(14f, 4f)
        )
    }
}

@Composable
fun BankVaultIcon() {
    Canvas(modifier = Modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        drawCircle(color = Color.DarkGray, radius = radius, center = center)
        drawCircle(color = Color.Gray, radius = radius * 0.8f, center = center, style = Stroke(width = 4f))
        drawCircle(color = Color(0xFF4DB6AC), radius = radius * 0.2f, center = center)
        for (i in 0 until 6) {
            val angle = i * (Math.PI / 3).toFloat()
            drawLine(
                color = Color.Gray,
                start = center,
                end = Offset(center.x + radius * 0.7f * cos(angle), center.y + radius * 0.7f * sin(angle)),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
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
        currentLyrics = "Zene és stream keresése a szervereken..."
        exoPlayer?.stop()
        
        coroutineScope.launch {
            try {
                val instances = listOf("pipedapi.kavin.rocks", "pipedapi.smnz.de", "api.piped.projectsegfau.lt", "pipedapi.adminforge.de")
                var playUrl = ""
                
                for (instance in instances) {
                    try {
                        val streamRequest = Request.Builder()
                            .url("https://$instance/streams/${song.audioUrl}")
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val streamResponse = withContext(Dispatchers.IO) { sharedHttpClient.newCall(streamRequest).execute() }
                        if (streamResponse.isSuccessful) {
                            val streamJson = JSONObject(streamResponse.body?.string() ?: "")
                            val audioStreams = streamJson.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                playUrl = audioStreams.getJSONObject(0).optString("url")
                                break
                            }
                        }
                    } catch (e: Exception) { continue }
                }
                
                if (playUrl.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        exoPlayer?.setMediaItem(MediaItem.fromUri(playUrl))
                        exoPlayer?.prepare()
                        exoPlayer?.play()
                        currentLyrics = "Dalszöveg keresése..."
                    }
                } else {
                    currentLyrics = "Hiba: A stream lejátszási hivatkozás nem szerezhető meg."
                }
            } catch (e: Exception) {
                currentLyrics = "Hiba a lejátszás betöltésekor."
            }

            val lyrics = fetchLyrics(song.artist, song.title)
            if (currentLyrics != "Hiba: A stream lejátszási hivatkozás nem szerezhető meg.") {
                currentLyrics = lyrics ?: "Nincs elérhető dalszöveg az interneten."
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés a YouTube-on (pl. Azahriah)...") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        aiStatus = "Keresés folyamatban..."
                        coroutineScope.launch {
                            val optimizedQuery = optimizeSearchWithGemini(query)
                            var results = searchYouTubeDirectly(optimizedQuery)
                            
                            if (results.isEmpty()) {
                                results = searchYouTubePiped(optimizedQuery)
                            }
                            
                            searchResults = results
                            aiStatus = if (results.isEmpty()) "Nincs találat." else null
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

suspend fun searchYouTubeDirectly(query: String): List<Song> = withContext(Dispatchers.IO) {
    val list = mutableListOf<Song>()
    try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "hu-HU,hu;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
            
        val response = sharedHttpClient.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext emptyList()
        
        val pattern = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([^\"]+)\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"")
        val matcher = pattern.matcher(html)
        
        while (matcher.find() && list.size < 15) {
            val videoId = matcher.group(1)
            val title = matcher.group(2)
            if (!videoId.isNullOrEmpty() && !title.isNullOrEmpty()) {
                list.add(Song(title = title, artist = "YouTube Videó", audioUrl = videoId))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext list
}

suspend fun searchYouTubePiped(query: String): List<Song> = withContext(Dispatchers.IO) {
    val instances = listOf("pipedapi.kavin.rocks", "pipedapi.adminforge.de", "api.piped.projectsegfau.lt")
    for (instance in instances) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url("https://$instance/search?q=$encodedQuery&filter=music_songs").build()
            val response = sharedHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonObject = JSONObject(response.body?.string() ?: "")
                if (jsonObject.has("items")) {
                    val jsonArray = jsonObject.getJSONArray("items")
                    val list = mutableListOf<Song>()
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val url = item.optString("url", "")
                        val videoId = url.replace("/watch?v=", "")
                        if (videoId.isNotEmpty()) {
                            list.add(Song(item.optString("title", "Ismeretlen"), item.optString("uploaderName", "Ismeretlen"), videoId))
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (e: Exception) { continue }
    }
    return@withContext emptyList()
}

suspend fun optimizeSearchWithGemini(userQuery: String): String = withContext(Dispatchers.IO) {
    try {
        val jsonBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", "Készíts ebből tiszta YouTube keresőkifejezést (csak előadó és cím): '$userQuery'")))))
        }
        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY")
            .post(requestBody)
            .build()

        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseJson = JSONObject(response.body?.string() ?: "")
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val text = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) return@withContext text.trim()
            }
        }
    } catch (e: Exception) {}
    return@withContext userQuery
}

suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    try {
        val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val request = Request.Builder().url("https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle").build()
        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "")
            val lyrics = json.optString("lyrics")
            if (lyrics.isNotEmpty()) return@withContext lyrics
        }
    } catch (e: Exception) {}
    return@withContext null
}

@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Nyomd meg a gombot a zenefelismerés indításához!") }
    val coroutineScope = rememberCoroutineScope()
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "A mikrofon engedély szükséges a felismeréshez", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@IconButton
                }

                if (!isRecording) {
                    try {
                        val file = File(context.cacheDir, "recorded_audio.3gp")
                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        recorder.setOutputFile(file.absolutePath)
                        recorder.prepare()
                        recorder.start()
                        
                        mediaRecorder = recorder
                        audioFile = file
                        isRecording = true
                        resultText = "Rögzítés folyamatban (5 mp)..."

                        coroutineScope.launch {
                            delay(5000)
                            try {
                                recorder.stop()
                                recorder.release()
                            } catch (e: Exception) {}
                            mediaRecorder = null
                            isRecording = false
                            resultText = "Elemzés a Gemini AI-val..."

                            if (file.exists()) {
                                val recognizedSong = recognizeAudioWithGemini(file)
                                resultText = recognizedSong
                            } else {
                                resultText = "Sikertelen hangfelvétel."
                            }
                        }
                    } catch (e: Exception) {
                        isRecording = false
                        resultText = "Hiba a rögzítés során: ${e.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.size(120.dp).clip(CircleShape).background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Mikrofon", tint = Color.White, modifier = Modifier.size(60.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(resultText, textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

suspend fun recognizeAudioWithGemini(audioFile: File): String = withContext(Dispatchers.IO) {
    try {
        val bytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val jsonBody = JSONObject().apply {
            val parts = org.json.JSONArray().apply {
                put(JSONObject().put("text", "Milyen zene/dal hallható ebben az audióban? Csak az előadót és a címet add meg!"))
                put(JSONObject().put("inlineData", JSONObject().apply {
                    put("mimeType", "audio/3gpp")
                    put("data", base64Audio)
                }))
            }
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", parts)))
        }

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY")
            .post(requestBody)
            .build()

        val response = sharedHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseJson = JSONObject(response.body?.string() ?: "")
            val text = responseJson.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
            
            if (!text.isNullOrBlank()) return@withContext text.trim()
        }
    } catch (e: Exception) {
        return@withContext "Hiba a felismeréskor: ${e.localizedMessage}"
    }
    return@withContext "Sajnos nem sikerült azonosítani a zenét."
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Bejelentkezve mint:", color = Color.Gray, fontSize = 14.sp)
        Text(user?.email ?: "Ismeretlen felhasználó", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kijelentkezés")
        }
    }
}
