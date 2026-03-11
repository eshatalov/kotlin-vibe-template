package com.github.testprocessor.testtable.stream.listener

import com.github.testprocessor.testtable.stream.properties.TestTableStreamProperties
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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val recordHandler: TestTableStreamRecordHandler,
    @Value("\${spring.application.name}") private val applicationName: String
) : InitializingBean, DisposableBean {

    private val consumerName = "$applicationName-${UUID.randomUUID()}"
    private val shuttingDown = AtomicBoolean(false)
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
            .concatMap(recordHandler::handleRecord)
            .retryWhen(
                // Stop retrying once the app is shutting down so connection-close signals
                // from Lettuce do not turn into noisy retry loops during a normal shutdown.
                Retry.backoff(Long.MAX_VALUE, retryDelay)
                    .maxBackoff(retryDelay)
                    .filter { !shuttingDown.get() }
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
            .subscribe(
                {},
                { exception ->
                    if (!shuttingDown.get()) {
                        logger.error(
                            "Test table stream listener stopped stream={} group={} consumer={}",
                            properties.key,
                            applicationName,
                            consumerName,
                            exception
                        )
                    }
                }
            )
    }

    override fun destroy() {
        shuttingDown.set(true)
        subscription?.dispose()
    }

    private fun ensureConsumerGroup() {
        try {
            reactiveStringRedisTemplate
                .opsForStream<String, String>()
                .createGroup(properties.key, ReadOffset.latest(), applicationName)
                .onErrorResume { exception ->
                    if (exception.isBusyGroupError()) {
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

private fun Throwable.isBusyGroupError(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.message?.contains("BUSYGROUP") == true) {
            return true
        }
        current = current.cause
    }
    return false
}
