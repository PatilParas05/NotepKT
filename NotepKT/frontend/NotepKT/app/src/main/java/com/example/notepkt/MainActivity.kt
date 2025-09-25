package com.example.notepkt

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.notepkt.NoteUi.LoginScreen
import com.example.notepkt.NoteUi.NoteScreen
import com.example.notepkt.NoteUi.SignupScreen
import com.example.notepkt.models.UserCredentials  // Make sure this import is correct
import com.example.notepkt.viewmodel.NoteViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val noteViewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val isLoggedIn by noteViewModel.isLoggedIn.collectAsState()
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                noteViewModel.checkServerConnection()
                noteViewModel.uiEvent.collectLatest { event ->
                    when (event) {
                        is NoteViewModel.UiEvent.ShowToast -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is NoteViewModel.UiEvent.NavigateToLogin -> {
                            navController.navigate("login") {
                                popUpTo("signup") { inclusive = true }
                            }
                        }
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn) "notes" else "signup"
            ) {
                composable("signup") {
                    SignupScreen(
                        onSignup = { credentials: UserCredentials ->
                            noteViewModel.signup(credentials)
                        },
                        onNavigateToLogin = { navController.navigate("login") }
                    )
                }
                composable("login") {
                    LoginScreen(
                        onLogin = { credentials: UserCredentials ->
                            noteViewModel.login(credentials)
                        },
                        onNavigateToSignup = { navController.navigate("signup") }
                    )
                }
                composable("notes") {
                    NoteScreen(
                        noteViewModel = noteViewModel,
                        onLogout = {
                            noteViewModel.logout()
                            navController.navigate("login") {
                                popUpTo("notes") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}