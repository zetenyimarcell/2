package com.hangfolyam.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import kotlinx.coroutines.launch

data class Song(val id: String, val title: String, val artist: String, val coverUrl: String, val streamUrl: String)

val globalMusicDatabase = listOf(
    Song("1", "Moonlight Sonata", "Beethoven", "https://upload.wikimedia.org/wikipedia/commons/1/10/Beethoven_Moonlight_Sonata.jpg", "https://upload.wikimedia.org/wikipedia/commons/e/e0/Beethoven%27s_Piano_Sonata_No._14_%28Moonlight%29_-_1._Adagio_sostenuto.ogg"),
    Song("2", "Spring", "Vivaldi", "https://upload.wikimedia.org/wikipedia/commons/4/4c/Vivaldi_four_seasons.jpg", "https://upload.wikimedia.org/wikipedia/commons/b/b3/Vivaldi_-_Spring_mvt_1_Allegro.ogg")
)

class MainActivity : ComponentActivity() {
    private val WEB_CLIENT_ID = "592646172227-d2kic3r4aj2pb8p2tijbasnc1ss1uo2s.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNavigator(WEB_CLIENT_ID)
            }
        }
    }
}

@Composable
fun AppNavigator(clientId: String) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    if (!isLoggedIn) {
        LoginScreen(clientId = clientId, error = loginError, onLoginSuccess = { isLoggedIn = true }, onError = { loginError = it })
    } else {
        MainScreenWithNavigation()
    }
}

@Composable
fun LoginScreen(clientId: String, error: String?, onLoginSuccess: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hangfolyam", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Zene minden eszközödön.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            onClick = {
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).setNonce("nonce_for_security").build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                    credentialManager.getCredential(context, request)
                    onLoginSuccess() 
                } catch (e: Exception) {
                    onError("Egyelőre csak tesztelőként tudsz belépni. A Google belépéshez hiányzik az SHA-1 kulcs a Firebase-ből!")
                }
            }
        }) {
            Text("Bejelentkezés Google Fiókkal")
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { onLoginSuccess() }) {
                Text("Belépés tesztelőként (kód megtekintése)")
            }
        }
    }
}

@Composable
fun MainScreenWithNavigation() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val exoPlayer = remember { ExoPlayer.Builder(LocalContext.current).build() }
    var currentlyPlaying by remember { mutableStateOf<Song?>(null) }
    
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Scaffold(
        bottomBar = {
            Column {
                if (currentlyPlaying != null) {
                    PlayerBar(currentlyPlaying!!, exoPlayer)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Zene") },
                        label = { Text("Zene") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (selectedTab == 0) {
                MusicListScreen(exoPlayer = exoPlayer, onPlay = { currentlyPlaying = it })
            } else {
                ProfileScreen()
            }
        }
    }
}

@Composable
fun MusicListScreen(exoPlayer: ExoPlayer, onPlay: (Song) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = globalMusicDatabase.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Keresés a zenék között...") },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredSongs) { song ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                        exoPlayer.setMediaItem(MediaItem.fromUri(song.streamUrl))
                        exoPlayer.prepare()
                        exoPlayer.play()
                        onPlay(song)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = song.coverUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(song.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(Icons.Default.Person, contentDescription = "Profil Kép", modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Saját Profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Az adatbázis szinkronizáció előkészítve (Firestore). Amint belépsz a Google fiókoddal, ide fog kerülni a hallgatási történeted!", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun PlayerBar(song: Song, exoPlayer: ExoPlayer) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = song.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Lejátszás/Szünet", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
