package com.github.template.testtable.stream

import com.github.template.client.model.TestTableResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "app.redis.streams.test-table",
    name = ["enabled"],
    havingValue = "false"
)
class NoOpTestTableEventPublisher : TestTableEventPublisher {

    override suspend fun publishCreated(response: TestTableResponse) = Unit

    override suspend fun publishUpdated(response: TestTableResponse) = Unit
}
