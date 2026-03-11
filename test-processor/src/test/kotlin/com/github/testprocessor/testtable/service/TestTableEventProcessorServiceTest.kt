package com.github.testprocessor.testtable.service

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import com.github.testprocessor.testtable.client.TemplateTestTableClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableEventProcessorServiceTest {

    private val templateTestTableClient = mockk<TemplateTestTableClient>()
    private val service = TestTableEventProcessorService(templateTestTableClient)

    @Test
    fun `created event should update template with processed description`(): Unit = runBlocking {
        val response = testTableResponse(description = "Test description")
        val requestSlot = slot<SaveTestTableRequest>()
        val expectedDescription = response.metadata.description + TestTableEventProcessorService.PROCESSED_DESCRIPTION_SUFFIX
        coEvery { templateTestTableClient.update(response.id, capture(requestSlot)) } returns response.copy(
            metadata = response.metadata.copy(description = expectedDescription)
        )

        service.process(TestTableStreamMessage(TestTableEventType.CREATED, response))

        assertThat(requestSlot.captured.metadata.description).isEqualTo(expectedDescription)
        coVerify { templateTestTableClient.update(response.id, requestSlot.captured) }
    }

    @Test
    fun `created event should fail when template returns unexpected description`(): Unit = runBlocking {
        val response = testTableResponse(description = "Test description")
        coEvery { templateTestTableClient.update(response.id, any()) } returns response

        assertThatThrownBy {
            runBlocking {
                service.process(TestTableStreamMessage(TestTableEventType.CREATED, response))
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Template returned unexpected description for processed create event")
    }

    @Test
    fun `updated event should only log`(): Unit = runBlocking {
        val response = testTableResponse(description = "Test description")

        service.process(TestTableStreamMessage(TestTableEventType.UPDATED, response))

        coVerify(exactly = 0) { templateTestTableClient.update(any(), any()) }
    }

    @Test
    fun `deleted event should only log`(): Unit = runBlocking {
        val response = testTableResponse(description = "Test description")

        service.process(TestTableStreamMessage(TestTableEventType.DELETED, response))

        coVerify(exactly = 0) { templateTestTableClient.update(any(), any()) }
    }

    private fun testTableResponse(description: String): TestTableResponse {
        return TestTableResponse(
            id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
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
