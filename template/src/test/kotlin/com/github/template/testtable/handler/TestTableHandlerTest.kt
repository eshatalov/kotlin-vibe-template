package com.github.template.testtable.handler

import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import com.github.template.testtable.service.NotFoundException
import com.github.template.testtable.service.TestTableService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableHandlerTest {

    private lateinit var webTestClient: WebTestClient
    private lateinit var service: TestTableService

    @BeforeEach
    fun setUp() {
        service = mockk()
        val handler = TestTableHandler(service)
        val router = TestTableRouter()
        val routerFunction = router.testTableRoutes(handler)

        webTestClient = WebTestClient.bindToRouterFunction(routerFunction)
            .configureClient()
            .baseUrl("/api/test-tables")
            .build()
    }

    @Test
    fun `GET should return all items`() {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadata = TestTableMetadata(item = "Test Item", description = "Test description")

        val id1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val id2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val response1 = TestTableResponse(id1, "Item 1", eventDate, eventTimestamp, metadata, now, now)
        val response2 = TestTableResponse(id2, "Item 2", LocalDate.of(2024, 2, 20), OffsetDateTime.parse("2024-02-20T15:45:00+00:00"), TestTableMetadata(item = "Item 2", description = "Description 2"), now, now)
        coEvery { service.findAll() } returns flowOf(response1, response2)

        webTestClient.get()
            .uri("")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(id1.toString())
            .jsonPath("$[0].name").isEqualTo("Item 1")
            .jsonPath("$[0].eventDate").isEqualTo("2024-01-15")
            .jsonPath("$[0].metadata.item").isEqualTo("Test Item")
            .jsonPath("$[1].id").isEqualTo(id2.toString())
            .jsonPath("$[1].name").isEqualTo("Item 2")
    }

    @Test
    fun `GET by id should return item`() {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadata = TestTableMetadata(item = "Test Item", description = "Test description")

        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val response = TestTableResponse(id, "Test Item", eventDate, eventTimestamp, metadata, now, now)
        coEvery { service.findById(id) } returns response

        webTestClient.get()
            .uri("/$id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.name").isEqualTo("Test Item")
            .jsonPath("$.eventDate").isEqualTo("2024-01-15")
            .jsonPath("$.metadata.item").isEqualTo("Test Item")
            .jsonPath("$.metadata.description").isEqualTo("Test description")
    }

    @Test
    fun `GET by id should return 404 when not found`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        coEvery { service.findById(id) } throws NotFoundException("TestTable with id $id not found")

        webTestClient.get()
            .uri("/$id")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `POST should create item and return 201`() {
        val now = OffsetDateTime.now()
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadata = TestTableMetadata(item = "New Item", description = "New description")
        val response = TestTableResponse(id, "New Item", eventDate, eventTimestamp, metadata, now, now)
        coEvery { service.create(any()) } returns response

        webTestClient.post()
            .uri("")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "name": "New Item",
                    "eventDate": "2024-01-15",
                    "eventTimestamp": "2024-01-15T10:30:00+00:00",
                    "metadata": {
                        "item": "New Item",
                        "description": "New description"
                    }
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.name").isEqualTo("New Item")
            .jsonPath("$.eventDate").isEqualTo("2024-01-15")
            .jsonPath("$.metadata.item").isEqualTo("New Item")
    }

    @Test
    fun `PUT should update item and return 200`() {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 2, 20)
        val eventTimestamp = OffsetDateTime.parse("2024-02-20T15:45:00+00:00")
        val metadata = TestTableMetadata(item = "Updated Item", description = "Updated description")

        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val response = TestTableResponse(id, "Updated Item", eventDate, eventTimestamp, metadata, now, now)
        coEvery { service.update(id, any()) } returns response

        webTestClient.put()
            .uri("/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "name": "Updated Item",
                    "eventDate": "2024-02-20",
                    "eventTimestamp": "2024-02-20T15:45:00+00:00",
                    "metadata": {
                        "item": "Updated Item",
                        "description": "Updated description"
                    }
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.name").isEqualTo("Updated Item")
            .jsonPath("$.eventDate").isEqualTo("2024-02-20")
            .jsonPath("$.metadata.item").isEqualTo("Updated Item")
    }

    @Test
    fun `PUT should return 404 when updating non-existent item`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        coEvery { service.update(id, any()) } throws NotFoundException("TestTable with id $id not found")

        webTestClient.put()
            .uri("/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                    "name": "Updated Item",
                    "eventDate": "2024-01-01",
                    "eventTimestamp": "2024-01-01T00:00:00+00:00",
                    "metadata": {
                        "item": "Test",
                        "description": "Test description"
                    }
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `DELETE should delete item and return 204`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        coEvery { service.delete(id) } returns Unit

        webTestClient.delete()
            .uri("/$id")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `DELETE should return 404 when deleting non-existent item`() {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        coEvery { service.delete(id) } throws NotFoundException("TestTable with id $id not found")

        webTestClient.delete()
            .uri("/$id")
            .exchange()
            .expectStatus().isNotFound
    }
}
