package com.github.testprocessor.testtable.stream.listener

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import com.github.testprocessor.testtable.service.TestTableEventProcessorService
import com.github.testprocessor.testtable.stream.converter.TestTableStreamMessageConverter
import com.github.testprocessor.testtable.stream.properties.TestTableStreamProperties
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveStreamOperations
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableStreamRecordHandlerTest {

    private val reactiveStringRedisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val reactiveStreamOperations = mockk<ReactiveStreamOperations<String, String, String>>()
    private val converter = mockk<TestTableStreamMessageConverter>()
    private val eventProcessorService = mockk<TestTableEventProcessorService>()
    private val properties = TestTableStreamProperties(
        enabled = true,
        key = "template:test-table-events",
        pollTimeout = Duration.ofSeconds(1)
    )
    private val handler = TestTableStreamRecordHandler(
        reactiveStringRedisTemplate = reactiveStringRedisTemplate,
        properties = properties,
        converter = converter,
        eventProcessorService = eventProcessorService,
        applicationName = "TestProcessor"
    )

    @Test
    fun `handleRecord should process message and acknowledge record`() {
        val recordId = RecordId.of("1-0")
        val record = mockk<MapRecord<String, String, String>>()
        val message = TestTableStreamMessage(TestTableEventType.CREATED, testTableResponse())
        every { record.id } returns recordId
        every { record.stream } returns "template:test-table-events"
        every { converter.fromRecord(record) } returns message
        every { reactiveStringRedisTemplate.opsForStream<String, String>() } returns reactiveStreamOperations
        every { reactiveStreamOperations.acknowledge(properties.key, "TestProcessor", recordId) } returns Mono.just(1L)
        coJustRun { eventProcessorService.process(message) }

        handler.handleRecord(record).block()

        coVerify { eventProcessorService.process(message) }
        verify { reactiveStreamOperations.acknowledge(properties.key, "TestProcessor", recordId) }
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
