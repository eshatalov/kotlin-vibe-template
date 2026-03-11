package com.github.testprocessor.testtable.service

import com.github.template.message.TestTableEventType
import com.github.template.message.TestTableStreamMessage
import com.github.testprocessor.testtable.client.TemplateTestTableClient
import com.github.testprocessor.testtable.mapper.toProcessedUpdateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TestTableEventProcessorService(
    private val templateTestTableClient: TemplateTestTableClient
) {

    suspend fun process(message: TestTableStreamMessage) {
        when (message.eventType) {
            TestTableEventType.CREATED -> processCreated(message)
            TestTableEventType.UPDATED -> {
                logger.info(
                    "Received test table update event id={} description={}",
                    message.response.id,
                    message.response.metadata.description
                )
            }
            TestTableEventType.DELETED -> {
                logger.info(
                    "Received test table delete event id={} description={}",
                    message.response.id,
                    message.response.metadata.description
                )
            }
        }
    }

    private suspend fun processCreated(message: TestTableStreamMessage) {
        val request = message.response.toProcessedUpdateRequest(PROCESSED_DESCRIPTION_SUFFIX)
        val updatedResponse = templateTestTableClient.update(message.response.id, request)
        if (updatedResponse.id != message.response.id) {
            logger.error(
                "Template returned unexpected id for processed create event expectedId={} actualId={}",
                message.response.id,
                updatedResponse.id
            )
            throw IllegalStateException("Template returned unexpected id for processed create event")
        }
        if (updatedResponse.metadata.description != request.metadata.description) {
            logger.error(
                "Template returned unexpected description for processed create event id={} expected={} actual={}",
                message.response.id,
                request.metadata.description,
                updatedResponse.metadata.description
            )
            throw IllegalStateException("Template returned unexpected description for processed create event")
        }
        logger.info(
            "Processed test table create event id={} updatedDescription={}",
            message.response.id,
            updatedResponse.metadata.description
        )
    }

    companion object {
        const val PROCESSED_DESCRIPTION_SUFFIX = " [processed by test-processor]"
        private val logger = LoggerFactory.getLogger(TestTableEventProcessorService::class.java)
    }
}
