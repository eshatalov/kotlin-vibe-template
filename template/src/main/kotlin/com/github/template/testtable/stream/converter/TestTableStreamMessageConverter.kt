package com.github.template.testtable.stream.converter

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.template.model.TestTableResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class TestTableStreamMessageConverter(
    private val objectMapper: ObjectMapper
) {

    fun toRecord(streamKey: String, message: TestTableStreamMessage): MapRecord<String, String, String> {
        return try {
            StreamRecords.newRecord()
                .`in`(streamKey)
                .ofMap(
                    mapOf(
                        EVENT_TYPE_FIELD to message.eventType.name,
                        PAYLOAD_FIELD to objectMapper.writeValueAsString(message.response)
                    )
                )
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to serialize test table stream message", exception)
        }
    }

    fun fromRecord(record: MapRecord<String, String, String>): TestTableStreamMessage {
        try {
            val eventType = TestTableEventType.valueOf(
                record.value[EVENT_TYPE_FIELD]
                    ?: throw IllegalArgumentException("Missing field '$EVENT_TYPE_FIELD'")
            )
            val payload = record.value[PAYLOAD_FIELD]
                ?: throw IllegalArgumentException("Missing field '$PAYLOAD_FIELD'")
            val response = objectMapper.readValue(payload, TestTableResponse::class.java)
            return TestTableStreamMessage(eventType = eventType, response = response)
        } catch (exception: IllegalArgumentException) {
            logger.error(
                "Failed to deserialize incoming test table stream record stream={} recordId={}",
                record.stream,
                record.id.value,
                exception
            )
            throw exception
        } catch (exception: Exception) {
            logger.error(
                "Failed to deserialize incoming test table stream record stream={} recordId={}",
                record.stream,
                record.id.value,
                exception
            )
            throw IllegalArgumentException("Failed to deserialize test table stream message", exception)
        }
    }

    companion object {
        private const val EVENT_TYPE_FIELD = "eventType"
        private const val PAYLOAD_FIELD = "payload"
        private val logger = LoggerFactory.getLogger(TestTableStreamMessageConverter::class.java)
    }
}
