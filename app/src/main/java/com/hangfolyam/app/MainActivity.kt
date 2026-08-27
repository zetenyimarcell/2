package com.hangfolyam.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LiveSong(val id: String, val title: String, val artist: String, val coverUrl: String, val streamUrl: String)

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0D0D19), surface = Color(0xFF1A1A2E), primary = Color(0xFF1DB954))) {
                AppRoot(WEB_CLIENT_ID)
            }
        }
    }
}

@Composable
fun AppRoot(clientId: String) {
    var currentScreen by remember { mutableStateOf("LOGIN") }
    Crossfade(targetState = currentScreen, animationSpec = tween(500), label = "Transition") { screen ->
        when (screen) {
            "LOGIN" -> LoginScreen(clientId = clientId, onTestBypass = { currentScreen = "HOME" }, onPhoneLoginClick = { currentScreen = "PHONE_LOGIN" })
            "PHONE_LOGIN" -> PhoneLoginScreen(onBack = { currentScreen = "LOGIN" }, onSuccess = { currentScreen = "HOME" })
            "HOME" -> SmoothMusicApp()
        }
    }
}

@Composable
fun LoginScreen(clientId: String, onTestBypass: () -> Unit, onPhoneLoginClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16222A), Color(0xFF3A6073))))) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hangfolyam", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text("A világ összes zenéje. Egy helyen.", fontSize = 16.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).setNonce("nonce").build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                            credentialManager.getCredential(context, request)
                            // Ha sikerül (Firebase beállítás kész), belép:
                            onTestBypass() 
                        } catch (e: Exception) {
                            Toast.makeText(context, "Hiba! Beállítottad a Firebase SHA-1 kulcsot?", Toast.LENGTH_LONG).show()
                            // IDEIGLENESEN MEGENGEDJÜK A TOVÁBBLÉPÉST A ZENÉKHEZ:
                            onTestBypass()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Folytatás Google-fiókkal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onPhoneLoginClick,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Belépés Telefonszámmal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun PhoneLoginScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var smsCode by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D19)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!codeSent) "Mi a telefonszámod?" else "Írd be az SMS kódot!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = if (!codeSent) phoneNumber else smsCode,
                onValueChange = { if (!codeSent) phoneNumber = it else smsCode = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = { Text(if (!codeSent) "+36 30 123 4567" else "000000") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    Toast.makeText(context, "A Firebase SHA-1 kulcs beállítása szükséges a valós SMS küldéshez!", Toast.LENGTH_LONG).show()
                    // Teszt miatt egyelőre átenged:
                    if (!codeSent) codeSent = true else onSuccess()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (!codeSent) "Kód küldése" else "Belépés")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) { Text("Vissza", color = Color.Gray) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmoothMusicApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LiveSong>>(emptyList()) }
    var currentlyPlaying by remember { mutableStateOf<LiveSong?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = currentlyPlaying != null, enter = slideInVertically(initialOffsetY = { it }) + fadeIn()) {
                currentlyPlaying?.let { ModernPlayerBar(it, exoPlayer) }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E1E30), Color(0xFF0D0D19)))).padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(32.dp))
                Text("Dupla Motoros Kereső", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Deezer / iTunes adatbázis", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.length >= 2) {
                            isLoading = true
                            coroutineScope.launch {
                                searchResults = searchMusicFromInternetFull(query)
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
                    placeholder = { Text("Keress bármit a világon...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(searchResults) { song ->
                            ModernSongCard(song) {
                                currentlyPlaying = song
                                exoPlayer.setMediaItem(MediaItem.fromUri(song.streamUrl))
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSongCard(song: LiveSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF202030).copy(alpha = 0.7f)).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            Text(song.artist, color = Color.LightGray, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun ModernPlayerBar(song: LiveSong, exoPlayer: ExoPlayer) {
    Surface(color = Color(0xFF1A1A2E).copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth().navigationBarsPadding().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), shadowElevation = 24.dp) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = song.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(CircleShape))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, maxLines = 1)
                Text(song.artist, fontSize = 13.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }, modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        }
    }
}

// DUPLA KERESŐMOTOR: DEEZER + ITUNES BEKÖTÉSE (Ez felel a magyar zenékért!)
suspend fun searchMusicFromInternetFull(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LiveSong>()
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        // 1. MOTOR (DEEZER) - Kiváló magyar zenei lefedettség
        try {
            val dzUrl = URL("https://api.deezer.com/search?q=$encodedQuery&limit=15")
            val dzConn = dzUrl.openConnection() as HttpURLConnection
            if (dzConn.responseCode == 200) {
                val dzResp = dzConn.inputStream.bufferedReader().use { it.readText() }
                val dzArray = JSONObject(dzResp).getJSONArray("data")
                for (i in 0 until dzArray.length()) {
                    val item = dzArray.getJSONObject(i)
                    val previewUrl = item.optString("preview", "")
                    if (previewUrl.isNotEmpty()) {
                        results.add(
                            LiveSong(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                artist = item.getJSONObject("artist").optString("name"),
                                coverUrl = item.getJSONObject("album").optString("cover_medium", ""),
                                streamUrl = previewUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) { Log.e("Deezer", "Hiba: ${e.message}") }

        // 2. MOTOR (APPLE ITUNES) - Globális adatbázis
        try {
            val itUrl = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=15&country=HU")
            val itConn = itUrl.openConnection() as HttpURLConnection
            if (itConn.responseCode == 200) {
                val itResp = itConn.inputStream.bufferedReader().use { it.readText() }
                val itArray = JSONObject(itResp).getJSONArray("results")
                for (i in 0 until itArray.length()) {
                    val item = itArray.getJSONObject(i)
                    val previewUrl = item.optString("previewUrl", "")
                    if (previewUrl.isNotEmpty()) {
                        val title = item.optString("trackName", "")
                        // Ha még nincs benne a listában, hozzáadjuk (hogy ne legyen dupla)
                        if (results.none { it.title.equals(title, ignoreCase = true) }) {
                            results.add(
                                LiveSong(
                                    id = item.optString("trackId"),
                                    title = title,
                                    artist = item.optString("artistName"),
                                    coverUrl = item.optString("artworkUrl100", "").replace("100x100", "300x300"),
                                    streamUrl = previewUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("iTunes", "Hiba: ${e.message}") }

    } catch (e: Exception) {
        Log.e("ZeneKereso", "Fő hiba: ${e.message}")
    }
    return@withContext results
}
