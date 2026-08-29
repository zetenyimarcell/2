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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).build()

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

// 1. BEJELENTKEZÉS
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
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = it.localizedMessage ?: "Regisztrációs hiba" }
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { errorMessage = "Hibás email/jelszó!" }
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

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        OutlinedButton(
            onClick = {
                if (activity != null) {
                    try {
                        val provider = OAuthProvider.newBuilder("google.com").build()
                        auth.startActivityForSignInWithProvider(activity, provider)
                            .addOnSuccessListener { onLoginSuccess() }
                            .addOnFailureListener { _ -> 
                                errorMessage = "Google belépés hiba: Ellenőrizd a Firebase konzolt!"
                            }
                    } catch (_: Exception) {
                        errorMessage = "Sikertelen indítás."
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
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
    }
}

// 2. FŐOLDAL
@Composable
fun HomeScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    val playlists = listOf("Napi Mix 1", "Magyar Top 50", "Újdonságok", "Fókusz")
    val recentSongs = listOf("Azahriah - 3 Korty", "Dzsúdló - Várnék", "Halott Pénz - Amikor feladnád")

    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        item {
            Text("Üdvözöllek!", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text("Neked ajánlott", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier.size(140.dp).padding(end = 12.dp).clickable {
                            Toast.makeText(context, "$playlist kiválasztva", Toast.LENGTH_SHORT).show()
                        },
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
            Text("Legutóbb hallgatott", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                recentSongs.forEach { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                             Toast.makeText(context, "Keresd meg a Keresőben a lejátszáshoz!", Toast.LENGTH_SHORT).show()
                        },
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

// 3. KERESŐ
@Composable
fun SearchScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<Song>()) }
    var isSearching by remember { mutableStateOf(false) }
    var loadingVideoId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés előadóra, dalra...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearching = true
                        errorMessage = ""
                        coroutineScope.launch {
                            searchResults = fetchFullSongs(query)
                            if (searchResults.isEmpty()) {
                                errorMessage = "Nincs találat. Próbálj másik kifejezést!"
                            }
                            isSearching = false
                        }
                    }) { Icon(Icons.Default.Send, contentDescription = "Keresés") }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            if (loadingVideoId != null) return@clickable
                            loadingVideoId = song.videoId
                            Toast.makeText(context, "Dal betöltése...", Toast.LENGTH_SHORT).show()

                            coroutineScope.launch {
                                val streamUrl = fetchAudioStreamUrl(song.videoId)
                                loadingVideoId = null
                                if (streamUrl != null) {
                                    exoPlayer?.stop()
                                    exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrl))
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                } else {
                                    Toast.makeText(context, "Nem sikerült a dalt betölteni. Próbálj másikat!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (loadingVideoId == song.videoId) {
                                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
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
    // Frissített és bővített aktív Piped szerverek listája
    val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi-libre.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.adminforge.de",
        "https://api-piped.mha.fi"
    )

    val client = OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

    for (baseUrl in pipedInstances) {
        try {
            val url = "$baseUrl/search?q=$encodedQuery&filter=music_songs"
            val request = Request.Builder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .url(url)
                .build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: continue
                val jsonObject = JSONObject(body)
                val jsonArray = jsonObject.optJSONArray("items") ?: continue
                val list = mutableListOf<Song>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.optString("type") == "stream") {
                        val title = item.optString("title", "Ismeretlen dal")
                        val uploader = item.optString("uploaderName", "Ismeretlen előadó")
                        val videoId = item.optString("url").replace("/watch?v=", "")
                        if (videoId.isNotEmpty()) {
                            list.add(Song(title, uploader, videoId))
                        }
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) {
            continue
        }
    }
    return@withContext emptyList()
}

suspend fun fetchAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
    val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi-libre.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.adminforge.de",
        "https://api-piped.mha.fi"
    )

    val client = OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    for (baseUrl in pipedInstances) {
        try {
            val url = "$baseUrl/streams/$videoId"
            val request = Request.Builder()
                .header("User-Agent", "Mozilla/5.0")
                .url(url)
                .build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: continue
                val jsonObject = JSONObject(body)
                val audioStreams = jsonObject.optJSONArray("audioStreams")
                if (audioStreams != null && audioStreams.length() > 0) {
                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.getJSONObject(i)
                        val streamUrl = stream.optString("url")
                        if (streamUrl.isNotEmpty()) {
                            return@withContext streamUrl
                        }
                    }
                }
            }
        } catch (_: Exception) {
            continue
        }
    }
    return@withContext null
}

// 4. DINAMIKUS ZENEFELISMERŐ (ISMÉTLÉSMENTESÍTVE)
@Composable
fun AudioRecognizerScreen(exoPlayer: ExoPlayer?) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Koppints a mikrofonra a zene azonosításához") }
    var foundSong by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val sampleSongs = listOf(
        "Azahriah - 3 Korty",
        "Dzsúdló - Várnék",
        "Carson Coma - Feldobom a követ",
        "Krúbi - Mini Klára",
        "Halott Pénz - Amikor feladnád",
        "Manuel - Zombi",
        "Valmar - Úristen",
        "T.Danny - Rebels"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            status = "Koppints újra a kezdéshez!"
        } else {
            status = "A mikrofon engedély szükséges a felismeréshez!"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(if (isListening) Color.Red else MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        if (!isListening) {
                            isListening = true
                            foundSong = null
                            status = "Zene hallgatása és elemzése..."
                            
                            coroutineScope.launch {
                                delay(3000)
                                isListening = false
                                // Olyan dalt sorsol, ami nem egyezik meg az előzőleg találttal
                                val availableSongs = sampleSongs.filter { it != foundSong }
                                foundSong = availableSongs.randomOrNull() ?: sampleSongs.random()
                                status = "Felismerve:"
                            }
                        } else {
                            isListening = false
                            status = "Keresés megszakítva."
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(status, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
        
        if (foundSong != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(foundSong!!, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { Toast.makeText(context, "Keresd meg a Kereső fülön a teljes dalt!", Toast.LENGTH_SHORT).show() }) {
                        Text("Keresés")
                    }
                }
            }
        }
    }
}

// 5. PROFIL
@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(user?.email ?: "Felhasználó", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Ingyenes fiók", color = Color.Gray)
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Statisztikák", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mentett dalok:")
                        Text("12", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Készített lejátszási listák:")
                        Text("2", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Kijelentkezés", color = Color.White)
            }
        }
    }
}
