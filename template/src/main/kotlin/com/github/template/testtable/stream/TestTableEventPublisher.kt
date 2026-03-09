package com.github.template.testtable.stream

import com.github.template.client.model.TestTableResponse

interface TestTableEventPublisher {

    suspend fun publishCreated(response: TestTableResponse)

    suspend fun publishUpdated(response: TestTableResponse)
}
