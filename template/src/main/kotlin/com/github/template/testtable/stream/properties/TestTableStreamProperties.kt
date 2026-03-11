package com.github.template.testtable.stream.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties("app.redis.streams.test-table")
data class TestTableStreamProperties(
    var enabled: Boolean = true,
    var key: String = "template:test-table-events",
    var pollTimeout: Duration = Duration.ofSeconds(1)
)
