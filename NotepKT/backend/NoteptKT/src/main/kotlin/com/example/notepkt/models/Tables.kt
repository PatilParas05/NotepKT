package com.example.notepkt.models

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption

object UsersTable : IntIdTable("users") {
    val email: Column<String> = varchar("email", 255).uniqueIndex()
    val password: Column<String> = varchar("password", 255)
}

object NotesTable : IntIdTable("notes") {
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val title: Column<String> = varchar("title", 255)
    val content: Column<String> = varchar("content", 1024)
    val timestamp: Column<Long> = long("timestamp")
}