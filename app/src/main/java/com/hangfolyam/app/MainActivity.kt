package com.hangfolyam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
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
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Gyűjtemények") },
                        label = { Text("Gyűjtemények") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                        label = { Text("Profil") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> HomeScreen()
                    1 -> SearchScreen()
                    2 -> AudioRecognizerScreen()
                    3 -> CollectionsScreen()
                    4 -> ProfileScreen(onSignOut = {
                        auth.signOut()
                        currentUser = null
                    })
                }
            }
        }
    }
}

// FRESIÍTETT BEJELENTKEZÉS ÉS REGISZTRÁCIÓS KÉPERNYŐ
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isPhoneLogin by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hangfolyam", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        if (isPhoneLogin) {
            // Telefonszámos bejelentkezés mező
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Telefonszám (+36...)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* SMS kód küldése Firebase Auth PhoneProvider-rel */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kód küldése SMS-ben")
            }
        } else {
            // Felhasználónév / Email és Jelszó mezők
            if (isSignUp) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Felhasználónév") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

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
                        val auth = FirebaseAuth.getInstance()
                        if (isSignUp) {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener { errorMessage = it.localizedMessage ?: "Regisztrációs hiba" }
                        } else {
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onLoginSuccess() }
                                .addOnFailureListener { errorMessage = it.localizedMessage ?: "Bejelentkezési hiba" }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSignUp) "Regisztráció" else "Bejelentkezés")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nincs fiókod? / Van már fiókod? Váltó gomb
        TextButton(onClick = { 
            isSignUp = !isSignUp 
            isPhoneLogin = false
        }) {
            Text(if (isSignUp) "Van már fiókod? Bejelentkezés" else "Nincs fiókod? Regisztráció")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Alternatív bejelentkezési opciók
        OutlinedButton(
            onClick = { /* Google Sign-In intent indítása */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bejelentkezés Google-fiókkal")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { isPhoneLogin = !isPhoneLogin },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Phone, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isPhoneLogin) "Vissza az emailes belépéshez" else "Bejelentkezés telefonszámmal")
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Főoldal & Lejátszó", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Keresés előadók, számok alapján...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AudioRecognizerScreen() {
    var isListening by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { isListening = !isListening },
            modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(if (isListening) "Hallgatás..." else "Koppints a zenefelismeréshez", fontSize = 18.sp)
    }
}

@Composable
fun CollectionsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Saját Gyűjtemények", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(user?.email ?: "Felhasználó", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onSignOut) {
            Text("Kijelentkezés")
        }
    }
}
