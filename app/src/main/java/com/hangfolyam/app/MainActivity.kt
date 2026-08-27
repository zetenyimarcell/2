package com.hangfolyam.app

import android.os.Bundle
import android.util.Log // <-- EZ A SOR HIÁNYZOTT AZ ELŐBB!
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LiveSong(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val streamUrl: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                primary = Color(0xFF1DB954) 
            )) {
                SmoothMusicApp()
            }
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
            AnimatedVisibility(
                visible = currentlyPlaying != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
            ) {
                currentlyPlaying?.let { ModernPlayerBar(it, exoPlayer) }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF121212))))
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Keresés",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.length >= 3) {
                            isLoading = true
                            coroutineScope.launch {
                                searchResults = searchMusicFromInternet(query)
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
                    placeholder = { Text("Előadó, dal vagy album...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color(0xFF2B2B2B),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color(0xFF1DB954)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.coverUrl.replace("100x100", "300x300"),
            contentDescription = "Borító",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = song.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
fun ModernPlayerBar(song: LiveSong, exoPlayer: ExoPlayer) {
    Surface(
        color = Color(0xFF222222),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(50.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(text = song.artist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(
                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Lejátszás/Szünet", tint = Color.White)
            }
        }
    }
}

suspend fun searchMusicFromInternet(query: String): List<LiveSong> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LiveSong>()
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=30")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        
        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            val jsonArray = jsonObject.getJSONArray("results")
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val previewUrl = item.optString("previewUrl", "")
                if (previewUrl.isNotEmpty()) {
                    results.add(
                        LiveSong(
                            id = item.optString("trackId"),
                            title = item.optString("trackName", "Ismeretlen Dal"),
                            artist = item.optString("artistName", "Ismeretlen Előadó"),
                            coverUrl = item.optString("artworkUrl100", ""),
                            streamUrl = previewUrl
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e("ZeneKereso", "Hiba az internetes keresésnél: ${e.message}")
    }
    return@withContext results
}
