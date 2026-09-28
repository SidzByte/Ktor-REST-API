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

        // 1. GET /tasks: Fetch all tasks from PostgreSQL
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

        // 2. POST /tasks: Insert a new task into PostgreSQL
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

        // 3. DELETE /tasks/{id}: Delete a task by ID from PostgreSQL
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