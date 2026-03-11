package com.github.testprocessor.testtable.client

import com.github.template.client.TestTableApi
import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TemplateTestTableRestClientTest {

    private val testTableApi = mockk<TestTableApi>()
    private val client = TemplateTestTableRestClient(testTableApi)

    @Test
    fun `update should return template response`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val request = updateRequest(description = "Test description [processed by test-processor]")
        val response = testTableResponse(id = id, description = request.metadata.description)
        every { testTableApi.updateTestTable(id, request) } returns Mono.just(response)

        val result = client.update(id, request)

        assertThat(result).isEqualTo(response)
    }

    @Test
    fun `update should wrap client failures`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val request = updateRequest(description = "Test description [processed by test-processor]")
        every { testTableApi.updateTestTable(id, request) } returns Mono.error(RuntimeException("boom"))

        assertThatThrownBy { runBlocking { client.update(id, request) } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Failed to update template test table $id")
            .hasCauseInstanceOf(RuntimeException::class.java)
    }

    private fun updateRequest(description: String): SaveTestTableRequest {
        return SaveTestTableRequest(
            name = "Test Item",
            eventDate = LocalDate.of(2024, 1, 15),
            eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            metadata = TestTableMetadata(
                item = "Test Item",
                description = description
            )
        )
    }

    private fun testTableResponse(id: UUID, description: String): TestTableResponse {
        return TestTableResponse(
            id = id,
            name = "Test Item",
            eventDate = LocalDate.of(2024, 1, 15),
            eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            metadata = TestTableMetadata(
                item = "Test Item",
                description = description
            ),
            createdAt = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            updatedAt = OffsetDateTime.parse("2024-01-15T10:35:00+00:00")
        )
    }
}
