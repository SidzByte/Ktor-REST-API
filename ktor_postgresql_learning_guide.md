# From Zero to PostgreSQL: A First-Timer’s Guide to Building a Ktor REST API

Welcome to the ultimate learning revision guide! This document captures the complete journey of transforming a basic in-memory Ktor application into a production-ready REST API powered by a real **PostgreSQL** database, **JetBrains Exposed ORM**, **DBeaver**, and **Postman**.

---

## 📍 Table of Contents
1. [Project Overview & Architecture](#1-project-overview--architecture)
2. [Chapter 1: Resolving Kotlin Compiler & IDE Metadata Mismatches](#chapter-1-resolving-kotlin-compiler--ide-metadata-mismatches)
3. [Chapter 2: Understanding HTTP Routes & 404 Troubleshooting](#chapter-2-understanding-http-routes--404-troubleshooting)
4. [Chapter 3: Memory (RAM) vs. Permanent Storage (Database)](#chapter-3-memory-ram-vs-permanent-storage-database)
5. [Chapter 4: Setting Up PostgreSQL & DBeaver on Windows](#chapter-4-setting-up-postgresql--dbeaver-on-windows)
6. [Chapter 5: Connecting Ktor to PostgreSQL with JetBrains Exposed ORM](#chapter-5-connecting-ktor-to-postgresql-with-jetbrains-exposed-orm)
7. [Chapter 6: Debugging PostgreSQL Password Authentication](#chapter-6-debugging-postgresql-password-authentication)
8. [Chapter 7: Verifying API Calls in Postman & Visualizing in DBeaver](#chapter-7-verifying-api-calls-in-postman--visualizing-in-dbeaver)
9. [Summary Cheat Sheet for Future Projects](#summary-cheat-sheet-for-future-projects)

---

## 1. Project Overview & Architecture

When building a modern backend REST API, data flows through distinct layers:

```
[ Postman / Client ] 
        │  (1. HTTP GET / POST / DELETE)
        ▼
[ Ktor Framework (Routing.kt) ]
        │  (2. Kotlin Object Conversion)
        ▼
[ JetBrains Exposed ORM (Databases.kt) ]
        │  (3. SQL Queries)
        ▼
[ PostgreSQL Engine (Port 5432) ]  ◄──── [ DBeaver GUI Viewer ]
```

---

## Chapter 1: Resolving Kotlin Compiler & IDE Metadata Mismatches

### The Problem
During development, two common errors occurred:
1. **IDE Banner Warning:** `"Some Kotlin runtime libraries and 33 other jars have an unsupported binary format."`
2. **Gradle Build Failure:** `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.0.0.`

### The Root Cause
- **Ktor 3.6.0** (`io.ktor:ktor-version-catalog:3.6.0`) dependencies are compiled using **Kotlin metadata version 2.3.0+**.
- If your `gradle/libs.versions.toml` is set to an older version like `kotlin = "2.0.0"`, the Kotlin compiler cannot read the metadata of Ktor 3.6.0 JARs.

### The Fix
In [`gradle/libs.versions.toml`](file:///c:/Users/sidz0/Downloads/demo-api/gradle/libs.versions.toml), set the `kotlin` plugin version to `"2.4.0"` to match Ktor 3.6.0 requirements:

```toml
[versions]
kotlin = "2.4.0"
logback = "1.5.37"

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

> 💡 **Key Takeaway:** Never click the IDE's *"Downgrade all Kotlin runtime libraries"* banner button. It creates temporary local overrides that break Gradle builds. Always align versions in `libs.versions.toml`.

---

## Chapter 2: Understanding HTTP Routes & 404 Troubleshooting

### The Problem
Postman returned **`404 Not Found`** when making a `GET` request to `http://127.0.0.1:8080`.

### The Root Cause
In [`Routing.kt`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/kotlin/Routing.kt), the root endpoint `get("/")` was commented out, and the custom endpoint was declared at `/tasks`:

```kotlin
fun Application.configureRouting() {
    routing {
        // Defined Endpoint: GET /tasks
        get("/tasks") {
            call.respond(tasks)
        }
    }
}
```

### The Fix
Update the Postman request URL to match the exact path defined in `Routing.kt`:
- **Correct URL:** `http://127.0.0.1:8080/tasks`

---

## Chapter 3: Memory (RAM) vs. Permanent Storage (Database)

### Why Initial POST Requests Were Temporary
Initially, tasks were stored in a top-level Kotlin list:
```kotlin
val tasks = mutableListOf(
    Task(1, "Learn Backend", "Setup Ktor project"),
    Task(2, "Download Postman", "Test the API")
)
```
- **In-Memory Storage (RAM):** Adding items with `tasks.add(newTask)` works temporarily while the app is running.
- **Limitation:** As soon as you **restart or stop the server**, RAM is cleared, and all new items vanish!
- **Solution:** Connect to a disk-backed database (PostgreSQL) so data persists permanently across app restarts.

---

## Chapter 4: Setting Up PostgreSQL & DBeaver on Windows

### Key Concepts & Tool Roles
- **PostgreSQL (`postgresql-x64-18`):** The actual database server engine. It runs invisibly in the background on port `5432`.
- **pgAdmin 4:** The official web-based management tool bundled with the PostgreSQL installer.
- **DBeaver:** A universal desktop GUI app used to visually view, inspect, and edit tables and rows.
- **Stack Builder:** An optional add-on installer popup at the end of PostgreSQL setup (you can click **Cancel** on this popup!).

---

## Chapter 5: Connecting Ktor to PostgreSQL with JetBrains Exposed ORM

### 1. Dependencies added to [`build.gradle.kts`](file:///c:/Users/sidz0/Downloads/demo-api/build.gradle.kts)
```kotlin
// PostgreSQL & Database ORM (JetBrains Exposed)
implementation("org.jetbrains.exposed:exposed-core:0.56.0")
implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
implementation("org.postgresql:postgresql:42.7.4")
implementation("com.zaxxer:HikariCP:5.1.0")
```

### 2. Data Model ([`Task.kt`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/kotlin/Task.kt))
```kotlin
package com.example

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: Int? = null, // Auto-incremented Primary Key
    val name: String,
    val description: String
)
```

### 3. Database Schema & Connection ([`Databases.kt`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/kotlin/Databases.kt))
```kotlin
package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

// Define the database table schema
object TasksTable : Table("tasks") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = varchar("description", 500)

    override val primaryKey = PrimaryKey(id)
}

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

    // Automatically create table if it doesn't exist
    transaction {
        SchemaUtils.create(TasksTable)
    }
}
```

### 4. Database CRUD Routes ([`Routing.kt`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/kotlin/Routing.kt))
```kotlin
package com.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

fun Application.configureRouting() {
    routing {

        // 1. READ ALL TASKS
        get("/tasks") {
            val allTasks = transaction {
                TasksTable.selectAll().map { row ->
                    Task(
                        id = row[TasksTable.id],
                        name = row[TasksTable.name],
                        description = row[TasksTable.description]
                    )
                }
            }
            call.respond(allTasks)
        }

        // 2. CREATE A TASK
        post("/tasks") {
            val newTask = call.receive<Task>()
            val newId = transaction {
                TasksTable.insert { row ->
                    row[name] = newTask.name
                    row[description] = newTask.description
                } get TasksTable.id
            }

            val createdTask = newTask.copy(id = newId)
            call.respond(HttpStatusCode.Created, createdTask)
        }

        // 3. DELETE A TASK BY ID
        delete("/tasks/{id}") {
            val idParam = call.parameters["id"]?.toIntOrNull()
            if (idParam == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid Task ID")
                return@delete
            }

            val deletedCount = transaction {
                TasksTable.deleteWhere { TasksTable.id eq idParam }
            }

            if (deletedCount > 0) {
                call.respond(HttpStatusCode.OK, "Task deleted successfully!")
            } else {
                call.respond(HttpStatusCode.NotFound, "Task not found")
            }
        }
    }
}
```

---

## Chapter 6: Debugging PostgreSQL Password Authentication

### The Issue
HikariCP pool initialization failed with:
`org.postgresql.util.PSQLException: FATAL: password authentication failed for user "postgres"`

### The Diagnosis & Fix
1. Tested direct authentication via PowerShell / `psql.exe` with password `"admin"` — connection succeeded!
2. Updated [`src/main/resources/application.yaml`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/resources/application.yaml):
   ```yaml
   storage:
     jdbcURL: "jdbc:postgresql://localhost:5432/postgres"
     user: "postgres"
     password: "admin"
   ```
3. Updated default fallback in [`Databases.kt`](file:///c:/Users/sidz0/Downloads/demo-api/src/main/kotlin/Databases.kt) to `"admin"`.

---

## Chapter 7: Verifying API Calls in Postman & Visualizing in DBeaver

### Testing in Postman

#### 1. Create a Task (`POST http://127.0.0.1:8080/tasks`)
- **Headers:** `Content-Type: application/json`
- **Body (raw JSON):**
  ```json
  {
    "name": "Database Integration",
    "description": "Store data permanently in PostgreSQL!"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "id": 1,
    "name": "Database Integration",
    "description": "Store data permanently in PostgreSQL!"
  }
  ```

---

### Visualizing in DBeaver

1. Open **DBeaver** $\rightarrow$ Connect to PostgreSQL (`localhost:5432`).
2. Expand left tree: `postgres` $\rightarrow$ `Databases` $\rightarrow$ `postgres` $\rightarrow$ `Schemas` $\rightarrow$ `public` $\rightarrow$ `Tables`.
3. Double-click on **`tasks`** $\rightarrow$ Click the **Data** tab $\rightarrow$ Press **Refresh (`F5`)**.
4. You will see the exact row inserted by Postman!

---

## Summary Cheat Sheet for Future Projects

| Task | Command / Action |
|---|---|
| **Build Project** | `.\gradlew.bat build` |
| **Run Unit Tests** | `.\gradlew.bat test` |
| **Run Ktor App** | `.\gradlew.bat run` (or run `main.kt` in IDE) |
| **Direct PostgreSQL Connection** | `"E:\PostgreSQL_setup\bin\psql.exe" -U postgres` |
| **SQL Query in DBeaver** | `SELECT * FROM tasks;` |
