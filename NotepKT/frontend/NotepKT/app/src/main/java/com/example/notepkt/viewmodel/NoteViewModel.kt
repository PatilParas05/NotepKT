package com.example.notepkt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notepkt.models.Note
import com.example.notepkt.models.UserCredentials
import com.example.notepkt.models.UserResponse
import io.ktor.client.* 
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString // Required import
import kotlinx.serialization.json.Json

object ApiClient {
    // Make json instance accessible for manual serialization if needed elsewhere
    val json = Json {
        prettyPrint = false // Changed to false for debugging
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(ApiClient.json) // Use the accessible json instance
        }

        // Add logging to see what's being sent
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
}

class NoteViewModel : ViewModel() {

    private val client = ApiClient.client
    private val baseUrl = "http://10.0.2.2:8080" // Emulator localhost

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _currentUserId = MutableStateFlow<Int?>(null)

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToLogin : UiEvent()
    }

    fun checkServerConnection() {
        viewModelScope.launch {
            try {
                val response: String = client.get("$baseUrl/ping").body()
                _uiEvent.emit(UiEvent.ShowToast("Server connected: $response"))
                println("✅ Server connection successful: $response")
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Cannot connect to server"))
                println("❌ Connection check failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun signup(credentials: UserCredentials) {
        viewModelScope.launch {
            try {
                println("🚀 Starting signup process...")

                // Enhanced validation
                if (credentials.email.isBlank()) {
                    _uiEvent.emit(UiEvent.ShowToast("Email cannot be empty"))
                    println("❌ Email is blank")
                    return@launch
                }

                if (credentials.password.isBlank()) {
                    _uiEvent.emit(UiEvent.ShowToast("Password cannot be empty"))
                    println("❌ Password is blank")
                    return@launch
                }

                // Basic email validation
                if (!credentials.email.contains("@")) {
                    _uiEvent.emit(UiEvent.ShowToast("Please enter a valid email"))
                    println("❌ Invalid email format")
                    return@launch
                }

                println("📤 Sending signup request:")
                println("  Email: ${credentials.email}")
                println("  Password length: ${credentials.password.length}")

                // Manually serialize and log the JSON
                val jsonString = ApiClient.json.encodeToString(credentials)
                println("🔑 Manually Serialized JSON: $jsonString") // <-- ADDED THIS LINE

                val response: HttpResponse = client.post("$baseUrl/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(credentials) // Ktor will use its configured Json instance
                }

                println("📥 Response status: ${response.status}")

                when (response.status) {
                    HttpStatusCode.Created, HttpStatusCode.OK -> {
                        println("✅ Signup successful")
                        _uiEvent.emit(UiEvent.ShowToast("Signup successful! Please log in."))
                        _uiEvent.emit(UiEvent.NavigateToLogin)
                    }
                    HttpStatusCode.Conflict -> {
                        println("⚠️ Email already exists")
                        _uiEvent.emit(UiEvent.ShowToast("Email already exists."))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = try {
                            response.bodyAsText()
                        } catch (e: Exception) {
                            "Could not read error body"
                        }
                        println("❌ Bad request error: $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Bad request: $errorBody"))
                    }
                    else -> {
                        val errorBody = try {
                            response.bodyAsText()
                        } catch (e: Exception) {
                            "Unknown error"
                        }
                        println("❌ Signup failed: ${response.status} - $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Signup failed: $errorBody"))
                    }
                }
            } catch (e: Exception) {
                println("💥 Signup exception: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error: ${e.message ?: "Cannot connect to server"}"))
            }
        }
    }

    fun login(credentials: UserCredentials) {
        viewModelScope.launch {
            try {
                println("🚀 Starting login process...")

                // Enhanced validation
                if (credentials.email.isBlank()) {
                    _uiEvent.emit(UiEvent.ShowToast("Email cannot be empty"))
                    println("❌ Email is blank")
                    return@launch
                }

                if (credentials.password.isBlank()) {
                    _uiEvent.emit(UiEvent.ShowToast("Password cannot be empty"))
                    println("❌ Password is blank")
                    return@launch
                }

                println("📤 Sending login request:")
                println("  Email: ${credentials.email}")
                println("  Password length: ${credentials.password.length}")

                // Manually serialize and log the JSON
                val jsonString = ApiClient.json.encodeToString(credentials)
                println("🔑 Manually Serialized JSON for login: $jsonString")


                val response: HttpResponse = client.post("$baseUrl/login") {
                    contentType(ContentType.Application.Json)
                    setBody(credentials)
                }

                println("📥 Response status: ${response.status}")

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val userResponse = response.body<UserResponse>()
                        _currentUserId.value = userResponse.userId
                        _isLoggedIn.value = true
                        println("✅ Login successful for user ID: ${userResponse.userId}")
                        _uiEvent.emit(UiEvent.ShowToast("Login successful!"))
                        fetchNotes(userResponse.userId)
                    }
                    HttpStatusCode.Unauthorized -> {
                        println("⚠️ Invalid credentials")
                        _uiEvent.emit(UiEvent.ShowToast("Invalid email or password."))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = try {
                            response.bodyAsText()
                        } catch (e: Exception) {
                            "Could not read error body"
                        }
                        println("❌ Bad request error: $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Bad request: $errorBody"))
                    }
                    else -> {
                        val errorBody = try {
                            response.bodyAsText()
                        } catch (e: Exception) {
                            "Unknown error"
                        }
                        println("❌ Login failed: ${response.status} - $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Login failed: $errorBody"))
                    }
                }
            } catch (e: Exception) {
                println("💥 Login exception: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error: ${e.message ?: "Cannot connect to server"}"))
            }
        }
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUserId.value = null
        _notes.value = emptyList()
        println("📤 User logged out")
    }

    fun fetchNotes(userId: Int? = _currentUserId.value) {
        viewModelScope.launch {
            val uid = userId ?: return@launch
            try {
                println("📤 Fetching notes for user: $uid")
                val fetchedNotes: List<Note> = client.get("$baseUrl/notes/user/$uid").body()
                _notes.value = fetchedNotes
                println("✅ Fetched ${fetchedNotes.size} notes")
            } catch (e: Exception) {
                println("❌ Fetch notes error: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error fetching notes: ${e.message}"))
            }
        }
    }

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: return@launch

            if (title.isBlank()) {
                _uiEvent.emit(UiEvent.ShowToast("Title is required"))
                return@launch
            }

            try {
                val newNote = Note(
                    title = title.trim(),
                    content = content.trim(),
                    userId = userId
                )

                println("📤 Adding note: ${newNote.title}")
                val jsonString = ApiClient.json.encodeToString(newNote)
                println("🔑 Manually Serialized JSON for add note: $jsonString")


                val response: HttpResponse = client.post("$baseUrl/notes") {
                    contentType(ContentType.Application.Json)
                    setBody(newNote)
                }

                when (response.status) {
                    HttpStatusCode.Created -> {
                        println("✅ Note added successfully")
                        fetchNotes(userId)
                        _uiEvent.emit(UiEvent.ShowToast("Note added successfully"))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Bad request" }
                        println("❌ Add note bad request: $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Bad request: $errorBody"))
                    }
                    else -> {
                        println("❌ Failed to add note: ${response.status}")
                        _uiEvent.emit(UiEvent.ShowToast("Failed to add note: ${response.status}"))
                    }
                }
            } catch (e: Exception) {
                println("💥 Add note exception: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error adding note: ${e.message}"))
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: return@launch

            if (note.title.isBlank()) {
                _uiEvent.emit(UiEvent.ShowToast("Title is required"))
                return@launch
            }

            try {
                val noteWithUser = note.copy(
                    userId = userId,
                    title = note.title.trim(),
                    content = note.content.trim()
                )

                println("📤 Updating note ID: ${note.id}")
                val jsonString = ApiClient.json.encodeToString(noteWithUser)
                println("🔑 Manually Serialized JSON for update note: $jsonString")


                val response: HttpResponse = client.put("$baseUrl/notes/${note.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(noteWithUser)
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        println("✅ Note updated successfully")
                        fetchNotes(userId)
                        _uiEvent.emit(UiEvent.ShowToast("Note updated"))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Bad request" }
                        println("❌ Update note bad request: $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Bad request: $errorBody"))
                    }
                    HttpStatusCode.NotFound -> {
                        println("⚠️ Note not found")
                        _uiEvent.emit(UiEvent.ShowToast("Note not found"))
                    }
                    else -> {
                        println("❌ Failed to update note: ${response.status}")
                        _uiEvent.emit(UiEvent.ShowToast("Failed to update note: ${response.status}"))
                    }
                }
            } catch (e: Exception) {
                println("💥 Update note exception: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error updating note: ${e.message}"))
            }
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            val userId = _currentUserId.value ?: return@launch
            try {
                println("📤 Deleting note ID: $id for user: $userId")

                val response: HttpResponse = client.delete("$baseUrl/notes/$id") {
                    url { parameters.append("userId", userId.toString()) }
                }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        println("✅ Note deleted successfully")
                        fetchNotes(userId)
                        _uiEvent.emit(UiEvent.ShowToast("Note deleted"))
                    }
                    HttpStatusCode.BadRequest -> {
                        val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Bad request" }
                        println("❌ Delete note bad request: $errorBody")
                        _uiEvent.emit(UiEvent.ShowToast("Bad request: $errorBody"))
                    }
                    HttpStatusCode.NotFound -> {
                        println("⚠️ Note not found for deletion")
                        _uiEvent.emit(UiEvent.ShowToast("Note not found"))
                    }
                    else -> {
                        println("❌ Failed to delete note: ${response.status}")
                        _uiEvent.emit(UiEvent.ShowToast("Failed to delete note: ${response.status}"))
                    }
                }
            } catch (e: Exception) {
                println("💥 Delete note exception: ${e.message}")
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowToast("Error deleting note: ${e.message}"))
            }
        }
    }
}
