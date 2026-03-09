package com.github.template.testtable.stream

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "app.redis.streams.test-table",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class TestTableRedisStreamListener(
    connectionFactory: ReactiveRedisConnectionFactory,
    private val reactiveStringRedisTemplate: ReactiveStringRedisTemplate,
    private val properties: TestTableStreamProperties,
    private val converter: TestTableStreamMessageConverter,
    @Value("\${spring.application.name}") private val applicationName: String
) : InitializingBean, DisposableBean {

    private val consumerName = "$applicationName-${UUID.randomUUID()}"
    private val streamReceiverOptions: StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, String>> =
        StreamReceiver.StreamReceiverOptions.builder()
            .pollTimeout(properties.pollTimeout)
            .build()
    private val streamReceiver = StreamReceiver.create(
        connectionFactory,
        streamReceiverOptions
    )
    private var subscription: Disposable? = null

    override fun afterPropertiesSet() {
        ensureConsumerGroup()
        subscription = streamReceiver
            .receive(
                Consumer.from(applicationName, consumerName),
                StreamOffset.create(properties.key, ReadOffset.lastConsumed())
            )
            .concatMap(::handleRecord)
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, retryDelay)
                    .maxBackoff(retryDelay)
                    .doBeforeRetry { signal ->
                        logger.warn(
                            "Retrying test table stream listener stream={} group={} consumer={} attempt={}",
                            properties.key,
                            applicationName,
                            consumerName,
                            signal.totalRetries() + 1,
                            signal.failure()
                        )
                    }
            )
            .subscribe()
    }

    override fun destroy() {
        subscription?.dispose()
    }

    private fun handleRecord(record: MapRecord<String, String, String>): Mono<Void> {
        return Mono.fromCallable { converter.fromRecord(record) }
            .doOnNext { message ->
                logger.info(
                    "Received test table stream message stream={} recordId={} group={} consumer={} eventType={} response={}",
                    record.stream,
                    record.id.value,
                    applicationName,
                    consumerName,
                    message.eventType,
                    message.response
                )
            }
            .flatMap {
                reactiveStringRedisTemplate
                    .opsForStream<String, String>()
                    .acknowledge(properties.key, applicationName, record.id)
            }
            .then()
    }

    private fun ensureConsumerGroup() {
        try {
            reactiveStringRedisTemplate
                .opsForStream<String, String>()
                .createGroup(properties.key, ReadOffset.latest(), applicationName)
                .onErrorResume { exception ->
                    if (exception.message?.contains("BUSYGROUP") == true) {
                        Mono.empty()
                    } else {
                        Mono.error(exception)
                    }
                }
                .block()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Failed to create Redis stream consumer group '$applicationName' for stream '${properties.key}'",
                exception
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TestTableRedisStreamListener::class.java)
        private val retryDelay: Duration = Duration.ofSeconds(1)
    }
}
