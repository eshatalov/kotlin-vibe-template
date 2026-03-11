package com.github.template.testtable.stream.publisher

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.TestTableResponse
import com.github.template.testtable.stream.converter.TestTableStreamMessageConverter
import com.github.template.testtable.stream.properties.TestTableStreamProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "app.redis.streams.test-table",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class TestTableRedisStreamPublisher(
    private val reactiveStringRedisTemplate: ReactiveStringRedisTemplate,
    private val properties: TestTableStreamProperties,
    private val converter: TestTableStreamMessageConverter
) : TestTableEventPublisher {

    override suspend fun publishCreated(response: TestTableResponse) {
        publish(TestTableEventType.CREATED, response)
    }

    override suspend fun publishUpdated(response: TestTableResponse) {
        publish(TestTableEventType.UPDATED, response)
    }

    override suspend fun publishDeleted(response: TestTableResponse) {
        publish(TestTableEventType.DELETED, response)
    }

    private suspend fun publish(eventType: TestTableEventType, response: TestTableResponse) {
        try {
            val record = converter.toRecord(
                streamKey = properties.key,
                message = TestTableStreamMessage(eventType = eventType, response = response)
            )
            val recordId = reactiveStringRedisTemplate
                .opsForStream<String, String>()
                .add(record)
                .awaitSingle()
            logger.info(
                "Published test table stream message stream={} recordId={} eventType={} testTableId={}",
                properties.key,
                recordId.value,
                eventType,
                response.id
            )
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to publish test table stream message for entity ${response.id}",
                exception
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TestTableRedisStreamPublisher::class.java)
    }
}
