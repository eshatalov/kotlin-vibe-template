package com.github.testprocessor.testtable.stream.converter

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.stream.StreamRecords
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableStreamMessageConverterTest {

    private val objectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
    private val converter = TestTableStreamMessageConverter(objectMapper)

    @Test
    fun `should convert message to record and back`() {
        val response = testTableResponse()
        val message = TestTableStreamMessage(
            eventType = TestTableEventType.CREATED,
            response = response
        )

        val record = converter.toRecord("template:test-table-events", message)
        val restored = converter.fromRecord(record)

        assertThat(record.stream).isEqualTo("template:test-table-events")
        assertThat(restored).isEqualTo(message)
    }

    @Test
    fun `should throw IllegalArgumentException when payload is missing`() {
        val record = StreamRecords.newRecord()
            .`in`("template:test-table-events")
            .ofMap(mapOf("eventType" to TestTableEventType.CREATED.name))

        assertThatThrownBy { converter.fromRecord(record) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Missing field 'payload'")
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
            updatedAt = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        )
    }
}
