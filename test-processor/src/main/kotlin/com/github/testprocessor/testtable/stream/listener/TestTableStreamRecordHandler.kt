package com.github.testprocessor.testtable.stream.listener

import com.github.testprocessor.testtable.service.TestTableEventProcessorService
import com.github.testprocessor.testtable.stream.converter.TestTableStreamMessageConverter
import com.github.testprocessor.testtable.stream.properties.TestTableStreamProperties
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class TestTableStreamRecordHandler(
    private val reactiveStringRedisTemplate: ReactiveStringRedisTemplate,
    private val properties: TestTableStreamProperties,
    private val converter: TestTableStreamMessageConverter,
    private val eventProcessorService: TestTableEventProcessorService,
    @Value("\${spring.application.name}") private val applicationName: String
) {

    fun handleRecord(record: MapRecord<String, String, String>): Mono<Void> {
        return Mono.fromCallable { converter.fromRecord(record) }
            .doOnNext { message ->
                logger.info(
                    "Received test table stream message stream={} recordId={} group={} eventType={} testTableId={}",
                    record.stream,
                    record.id.value,
                    applicationName,
                    message.eventType,
                    message.response.id
                )
            }
            .flatMap { message -> mono { eventProcessorService.process(message) } }
            .flatMap {
                reactiveStringRedisTemplate
                    .opsForStream<String, String>()
                    .acknowledge(properties.key, applicationName, record.id)
            }
            .then()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TestTableStreamRecordHandler::class.java)
    }
}
