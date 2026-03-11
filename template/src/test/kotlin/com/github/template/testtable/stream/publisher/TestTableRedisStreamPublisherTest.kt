package com.github.template.testtable.stream.publisher

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import com.github.template.testtable.stream.converter.TestTableStreamMessageConverter
import com.github.template.testtable.stream.properties.TestTableStreamProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
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

class TestTableRedisStreamPublisherTest {

    private val reactiveStringRedisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val reactiveStreamOperations = mockk<ReactiveStreamOperations<String, String, String>>()
    private val converter = mockk<TestTableStreamMessageConverter>()
    private val properties = TestTableStreamProperties(
        enabled = true,
        key = "template:test-table-events",
        pollTimeout = Duration.ofSeconds(1)
    )
    private val publisher = TestTableRedisStreamPublisher(
        reactiveStringRedisTemplate = reactiveStringRedisTemplate,
        properties = properties,
        converter = converter
    )

    @Test
    fun `publishCreated should add created event to redis stream`(): Unit = runBlocking {
        assertPublish(TestTableEventType.CREATED) { response ->
            publisher.publishCreated(response)
        }
    }

    @Test
    fun `publishUpdated should add updated event to redis stream`(): Unit = runBlocking {
        assertPublish(TestTableEventType.UPDATED) { response ->
            publisher.publishUpdated(response)
        }
    }

    @Test
    fun `publishDeleted should add deleted event to redis stream`(): Unit = runBlocking {
        assertPublish(TestTableEventType.DELETED) { response ->
            publisher.publishDeleted(response)
        }
    }

    @Test
    fun `publish should wrap redis failures`(): Unit = runBlocking {
        val response = testTableResponse()
        val record = mockk<MapRecord<String, String, String>>()
        every {
            converter.toRecord(
                properties.key,
                TestTableStreamMessage(TestTableEventType.CREATED, response)
            )
        } returns record
        every { reactiveStringRedisTemplate.opsForStream<String, String>() } returns reactiveStreamOperations
        every { reactiveStreamOperations.add(record) } returns Mono.error(RuntimeException("boom"))

        assertThatThrownBy {
            runBlocking { publisher.publishCreated(response) }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Failed to publish test table stream message for entity ${response.id}")
            .hasCauseInstanceOf(RuntimeException::class.java)
    }

    private suspend fun assertPublish(
        eventType: TestTableEventType,
        publish: suspend (TestTableResponse) -> Unit
    ) {
        val response = testTableResponse()
        val record = mockk<MapRecord<String, String, String>>()
        every {
            converter.toRecord(
                properties.key,
                TestTableStreamMessage(eventType, response)
            )
        } returns record
        every { reactiveStringRedisTemplate.opsForStream<String, String>() } returns reactiveStreamOperations
        every { reactiveStreamOperations.add(record) } returns Mono.just(RecordId.of("1-0"))

        publish(response)

        verify {
            converter.toRecord(
                properties.key,
                TestTableStreamMessage(eventType, response)
            )
        }
        verify { reactiveStreamOperations.add(record) }
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
