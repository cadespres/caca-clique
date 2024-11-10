package ca.gfs.caca_cliqueur

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.gfs.caca_cliqueur.ui.theme.CacacliqueurTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialiser le MediaPlayer avec la musique de fond
        mediaPlayer = MediaPlayer.create(this, R.raw.background_music)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        setContent {
            CacacliqueurTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this,
                        onMuteToggle = { toggleMute() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!isMuted) {
            mediaPlayer.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        if (isMuted) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.start()
        }
    }
}

data class Quete(val description: String, val objectif: Int, var estTerminee: Boolean = false)

@Composable
fun GameScreen(modifier: Modifier = Modifier, context: Context, onMuteToggle: () -> Unit) {
    val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    var clickCount by remember { mutableStateOf(prefs.getInt("clickCount", 0)) }
    var multiplicateur by remember { mutableStateOf(prefs.getInt("multiplicateur", 1)) }
    var clicsParSeconde by remember { mutableStateOf(prefs.getInt("clicsParSeconde", 0)) }
    var multiplicateurCost by remember { mutableStateOf(prefs.getInt("multiplicateurCost", 10)) }
    var autoClickCost by remember { mutableStateOf(prefs.getInt("autoClickCost", 20)) }
    var scoreParClic by remember { mutableStateOf(0) }
    var afficherScoreParClic by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var leaderboard by remember { mutableStateOf(getLeaderboard(prefs)) }

    val quetes = remember {
        listOf(
            Quete("Atteindre 100 clics", 100),
            Quete("Atteindre 500 clics", 500),
            Quete("Faire 50 clics en 10 secondes", 50)
        )
    }

    fun saveProgress() {
        prefs.edit().apply {
            putInt("clickCount", clickCount)
            putInt("multiplicateur", multiplicateur)
            putInt("clicsParSeconde", clicsParSeconde)
            putInt("multiplicateurCost", multiplicateurCost)
            putInt("autoClickCost", autoClickCost)
            apply()
        }
    }

    fun updateLeaderboard(newScore: Int) {
        // Retrieve and update the current leaderboard
        val scores = getLeaderboard(prefs).toMutableList()
        scores.add(newScore)
        scores.sortDescending()

        // Keep only the top 10 scores
        val topScores = if (scores.size > 10) scores.take(10) else scores

        // Save the updated leaderboard in SharedPreferences
        prefs.edit().putStringSet("leaderboard", topScores.map { it.toString() }.toSet()).apply()
        leaderboard = topScores // Update the leaderboard state for real-time display
    }


    LaunchedEffect(clicsParSeconde) {
        while (true) {
            delay(1000L)
            if (clicsParSeconde > 0) {
                clickCount += clicsParSeconde
                verifierQuetes(clickCount, quetes)
                saveProgress()
                updateLeaderboard(clickCount)
            }
        }
    }

    LaunchedEffect(afficherScoreParClic) {
        if (afficherScoreParClic) {
            delay(500L)
            afficherScoreParClic = false
        }
    }

    fun restartGame() {
        updateLeaderboard(clickCount)
        clickCount = 0
        multiplicateur = 1
        clicsParSeconde = 0
        multiplicateurCost = 10
        autoClickCost = 20
        saveProgress()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxSize()) {
        Text(text = "Score: $clickCount", fontSize = 24.sp)

        if (afficherScoreParClic) {
            Text(text = "+$scoreParClic", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.turd),
            contentDescription = "Clickable turd",
            modifier = Modifier
                .size(200.dp)
                .clickable {
                    scoreParClic = multiplicateur
                    clickCount += scoreParClic
                    verifierQuetes(clickCount, quetes)
                    afficherScoreParClic = true
                    saveProgress()
                    updateLeaderboard(clickCount) // Mise à jour en temps réel du classement
                }
        )

        Spacer(modifier = Modifier.height(16.dp))

        quetes.forEach { quete ->
            Text("${quete.description} - ${if (quete.estTerminee) "Terminée" else "En cours"}")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (clickCount >= multiplicateurCost) {
                clickCount -= multiplicateurCost
                multiplicateur += 1
                multiplicateurCost += 10
                saveProgress()
            }
        }) {
            Text("Acheter multiplicateur (x$multiplicateur) - Coût: $multiplicateurCost")
        }

        Button(onClick = {
            if (clickCount >= autoClickCost) {
                clickCount -= autoClickCost
                clicsParSeconde += 1
                autoClickCost += 15
                saveProgress()
            }
        }) {
            Text("Acheter clic automatique ($clicsParSeconde/sec) - Coût: $autoClickCost")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("Recommencer la partie")
        }

        Button(onClick = { onMuteToggle() }) {
            Text("Mettre en sourdine")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Classement des meilleurs joueurs :", fontSize = 20.sp, color = Color.Blue)
        leaderboard.forEachIndexed { index, score ->
            Text(text = "${index + 1}. Score: $score clics", fontSize = 16.sp)
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = "Confirmation") },
                text = { Text(text = "Voulez-vous vraiment recommencer la partie ?") },
                confirmButton = {
                    TextButton(onClick = {
                        restartGame()
                        showDialog = false
                    }) {
                        Text("Oui")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Non")
                    }
                }
            )
        }
    }
}

fun getLeaderboard(prefs: android.content.SharedPreferences): List<Int> {
    return prefs.getStringSet("leaderboard", setOf())?.map { it.toInt() }?.sortedDescending() ?: emptyList()
}

fun verifierQuetes(clickCount: Int, quetes: List<Quete>) {
    quetes.forEach { quete ->
        if (!quete.estTerminee && clickCount >= quete.objectif) {
            quete.estTerminee = true
            println("Quête terminée : ${quete.description}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GameScreenPreview() {
    CacacliqueurTheme {
        GameScreen(context = androidx.compose.ui.platform.LocalContext.current, onMuteToggle = {})
    }
}
