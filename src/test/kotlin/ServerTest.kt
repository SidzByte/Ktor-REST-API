package com.example

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class ServerTest {

    @Test
    fun `test tasks endpoint`() = testApplication {
        application {
            // Setup in-memory H2 database for testing
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(TasksTable)
            }

            configureSerialization()
            configureRouting()
        }
        // verify tasks endpoint returns 200 OK
        assertEquals(HttpStatusCode.OK, client.get("/tasks").status)
    }

}
