package com.example.notepkt.plugins

import com.example.notepkt.models.NotesTable
import com.example.notepkt.models.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.TimeZone

object DatabaseFactory {
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = "jdbc:postgresql://localhost:5432/notep_db"
            username = "postgres"
            password = "paras@2005"
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(UsersTable, NotesTable)
        }
    }
}
