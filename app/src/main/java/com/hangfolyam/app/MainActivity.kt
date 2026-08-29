package com.hangfolyam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ========== ADATMODELL ==========
data class Song(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val audioUrl: String
)

// ========== FŐ AKTIVITÁS ==========
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    val playerViewModel: PlayerViewModel = viewModel()
                    
                    // Teszt adat
                    val sampleSong = Song(
                        title = "Példa Dal Címe",
                        artist = "Példa Előadó",
                        coverUrl = "",
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                    )
                    
                    val sampleLyrics = """
                        Ez itt a dalszöveg első sora.
                        Itt jön a második sor.
                        
                        Refrén:
                        Ez egy nagyon jó kis dal,
                        Szól a zene, senki nem zavar.
                        
                        Itt pedig folytatódik a versszak,
                        Görgethetsz is lefelé, ha hosszú.
                    """.trimIndent()

                    FullPlayerScreen(
                        song = sampleSong,
                        playerViewModel = playerViewModel,
                        lyrics = sampleLyrics
                    )
                }
            }
        }
    }
}

// ========== A LEJÁTSZÓ FELÜLET (UI) ==========
@Composable
fun FullPlayerScreen(song: Song, playerViewModel: PlayerViewModel, lyrics: String) {
    val context = LocalContext.current
    val player = playerViewModel.player

    // Állapotok a csúszkához és a gombokhoz
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }

    // Lejátszó inicializálása induláskor
    LaunchedEffect(Unit) {
        playerViewModel.initPlayer(context)
        playerViewModel.playAudio(song.audioUrl)
    }

    // Lejátszási idő (csúszka) folyamatos frissítése
    LaunchedEffect(player) {
        while (true) {
            player?.let {
                if (!seeking) {
                    position = it.currentPosition.toFloat()
                    duration = it.duration.coerceAtLeast(0).toFloat()
                    isPlaying = it.isPlaying
                }
            }
            delay(500) // Fél másodpercenként frissít
        }
    }

    // Fő elrendezés
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "BottomSheet" drag handle vonal felül
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Borítókép (Coil)
        AsyncImage(
            model = song.coverUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500" },
            contentDescription = "Album borító",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(300.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Cím és Előadó
        Text(
            text = song.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.artist,
            fontSize = 18.sp,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Lejátszási csúszka (Slider)
        Slider(
            value = position,
            onValueChange = { 
                position = it
                seeking = true 
            },
            onValueChangeFinished = {
                player?.seekTo(position.toLong())
                seeking = false
            },
            valueRange = 0f..(if (duration > 0f) duration else 1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Idő formázó segédfüggvény
        val formatTime = { ms: Float ->
            val totalSeconds = (ms / 1000).toInt().coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%d:%02d", minutes, seconds)
        }

        // Időkijelzés (Jelenlegi idő / Teljes idő)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = Color.Gray, fontSize = 12.sp)
            Text(formatTime(duration), color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Lejátszás vezérlő (Play/Pause)
        Row(
            verticalAlignment = Alignment.CenterHorizontally,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    if (player?.isPlaying == true) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player?.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isPlaying) "⏸" else "▶", fontSize = 32.sp, color = Color.Black)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Dalszöveg nézet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF16221B))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Dalszöveg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = lyrics,
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}
