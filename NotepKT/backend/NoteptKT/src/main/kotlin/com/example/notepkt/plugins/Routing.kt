package com.example.notepkt.plugins

import com.example.notepkt.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureRouting() {
    routing {
        get("/ping") {
            call.respondText("pong")
        }

        post("/signup") {
            try {
                val credentials = call.receive<UserCredentials>()

                // Validate input
                if (credentials.email.isBlank() || credentials.password.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Email and password are required")
                    )
                }

                val createdId = transaction {
                    val exists = UsersTable
                        .selectAll()
                        .where { UsersTable.email eq credentials.email }
                        .limit(1)
                        .singleOrNull()

                    if (exists != null) null
                    else {
                        val hashedPassword = BCrypt.hashpw(credentials.password, BCrypt.gensalt())
                        UsersTable.insertAndGetId {
                            it[email] = credentials.email
                            it[password] = hashedPassword
                        }.value
                    }
                }

                if (createdId != null)
                    call.respond(HttpStatusCode.Created, UserResponse(createdId))
                else
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Email already exists"))
            } catch (e: Exception) {
            println("Signup error raw message: ${e.message}") // Log the raw message
            println("Signup error full stack trace:")
            e.printStackTrace() // Print the full stack trace
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format or internal processing issue."))
        }
        }

        post("/login") {
            try {
                val credentials = call.receive<UserCredentials>()

                // Validate input
                if (credentials.email.isBlank() || credentials.password.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Email and password are required")
                    )
                }

                val userRow = transaction {
                    UsersTable
                        .selectAll()
                        .where { UsersTable.email eq credentials.email }
                        .limit(1)
                        .singleOrNull()
                }

                if (userRow != null) {
                    val hashedPassword = userRow[UsersTable.password]
                    if (BCrypt.checkpw(credentials.password, hashedPassword)) {
                        val userId = userRow[UsersTable.id].value
                        call.respond(HttpStatusCode.OK, UserResponse(userId))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                }
            } catch (e: Exception) {
                println("Login error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }

        get("/notes/user/{userId}") {
            try {
                val userId = call.parameters["userId"]?.toIntOrNull()
                if (userId == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
                }

                val notes = transaction {
                    NotesTable
                        .selectAll()
                        .where { NotesTable.userId eq userId }
                        .orderBy(NotesTable.timestamp to SortOrder.DESC)
                        .map { row ->
                            Note(
                                id = row[NotesTable.id].value,
                                userId = row[NotesTable.userId].value,
                                title = row[NotesTable.title],
                                content = row[NotesTable.content],
                                timestamp = row[NotesTable.timestamp]
                            )
                        }
                }

                call.respond(HttpStatusCode.OK, notes)
            } catch (e: Exception) {
                println("Get notes error: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch notes"))
            }
        }

        post("/notes") {
            try {
                val note = call.receive<Note>()

                // Better validation
                val uid = note.userId ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "userId is required")
                )

                if (note.title.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "title is required")
                    )
                }

                val now = System.currentTimeMillis()
                val newId = transaction {
                    NotesTable.insertAndGetId {
                        it[userId] = uid
                        it[title] = note.title
                        it[content] = note.content
                        it[timestamp] = now
                    }.value
                }

                call.respond(HttpStatusCode.Created, note.copy(id = newId, timestamp = now))
            } catch (e: Exception) {
                println("Add note error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }

        put("/notes/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                val updated = call.receive<Note>()

                if (id == null || updated.userId == null) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid note ID or userId")
                    )
                }

                if (updated.title.isBlank()) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "title is required")
                    )
                }

                val now = System.currentTimeMillis()
                val rows = transaction {
                    NotesTable.update({ (NotesTable.id eq id) and (NotesTable.userId eq updated.userId) }) {
                        it[title] = updated.title
                        it[content] = updated.content
                        it[timestamp] = now
                    }
                }

                if (rows > 0) {
                    call.respond(HttpStatusCode.OK, updated.copy(id = id, timestamp = now))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Note not found or you don't have permission")
                    )
                }
            } catch (e: Exception) {
                println("Update note error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }

        delete("/notes/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                val userId = call.request.queryParameters["userId"]?.toIntOrNull()

                if (id == null || userId == null) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid note ID or userId")
                    )
                }

                val rows = transaction {
                    NotesTable.deleteWhere { (NotesTable.id eq id) and (NotesTable.userId eq userId) }
                }

                if (rows > 0) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Note deleted"))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Note not found or you don't have permission")
                    )
                }
            } catch (e: Exception) {
                println("Delete note error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }
    }
}