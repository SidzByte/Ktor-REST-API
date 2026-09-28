package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

// 1. Define the PostgreSQL "tasks" Table Schema
object TasksTable : Table("tasks") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = varchar("description", 500)

    override val primaryKey = PrimaryKey(id)
}

// 2. Initialize Database Connection and Create Table
fun Application.configureDatabases() {
    val dbUrl = environment.config.propertyOrNull("storage.jdbcURL")?.getString()
        ?: "jdbc:postgresql://localhost:5432/postgres"
    val dbUser = environment.config.propertyOrNull("storage.user")?.getString()
        ?: "postgres"
    val dbPassword = environment.config.propertyOrNull("storage.password")?.getString()
        ?: "admin"

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        driverClassName = "org.postgresql.Driver"
        username = dbUser
        password = dbPassword
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    // Automatically create the 'tasks' table in PostgreSQL if it doesn't exist
    transaction {
        SchemaUtils.create(TasksTable)
    }
}
