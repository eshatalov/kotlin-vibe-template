package com.github.template.testtable.stream.publisher

import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class NoOpTestTableEventPublisherTest {

    private val publisher = NoOpTestTableEventPublisher()

    @Test
    fun `publish methods should complete without side effects`(): Unit = runBlocking {
        val response = testTableResponse()

        publisher.publishCreated(response)
        publisher.publishUpdated(response)
        publisher.publishDeleted(response)
    }

    private fun testTableResponse(): TestTableResponse {
        return TestTableResponse(
            id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            name = "Test Item",
            eventDate = LocalDate.of(2024, 1, 15),
            eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            metadata = TestTableMetadata(
                item = "Test Item",
                description = "Test description"
            ),
            createdAt = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            updatedAt = OffsetDateTime.parse("2024-01-15T10:35:00+00:00")
        )
    }
}
