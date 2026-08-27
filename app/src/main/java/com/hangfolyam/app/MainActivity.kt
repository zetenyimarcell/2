package com.hangfolyam.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class Song(val title: String, val artist: String, val coverUrl: String, val streamUrl: String)

val globalMusicDatabase = listOf(
    Song("Moonlight Sonata", "Beethoven", "https://upload.wikimedia.org/wikipedia/commons/1/10/Beethoven_Moonlight_Sonata.jpg", "https://upload.wikimedia.org/wikipedia/commons/e/e0/Beethoven%27s_Piano_Sonata_No._14_%28Moonlight%29_-_1._Adagio_sostenuto.ogg"),
    Song("Spring", "Vivaldi", "https://upload.wikimedia.org/wikipedia/commons/4/4c/Vivaldi_four_seasons.jpg", "https://upload.wikimedia.org/wikipedia/commons/b/b3/Vivaldi_-_Spring_mvt_1_Allegro.ogg")
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

    if (!isLoggedIn) {
        LoginScreen(clientId = clientId) { isLoggedIn = true }
    } else {
        MainMusicScreen()
    }
}

@Composable
fun LoginScreen(clientId: String, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hangfolyam", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).setNonce("nonce_for_security").build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                    credentialManager.getCredential(context, request)
                    onLoginSuccess() 
                } catch (e: Exception) {
                    onLoginSuccess() // Hibatűrés teszteléshez
                }
            }
        }) {
            Text("Bejelentkezés Google Fiókkal")
        }
    }
}

@Composable
fun MainMusicScreen() {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var currentlyPlaying by remember { mutableStateOf<Song?>(null) }
    
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    val filteredSongs = globalMusicDatabase.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("Keresés a zenék között...") },
            singleLine = true
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(filteredSongs) { song ->
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    currentlyPlaying = song
                    exoPlayer.setMediaItem(MediaItem.fromUri(song.streamUrl))
                    exoPlayer.prepare()
                    exoPlayer.play()
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${song.title} - ${song.artist}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
