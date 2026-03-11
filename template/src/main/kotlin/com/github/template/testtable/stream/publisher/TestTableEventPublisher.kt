package com.github.template.testtable.stream.publisher

import com.github.template.model.TestTableResponse

interface TestTableEventPublisher {

    suspend fun publishCreated(response: TestTableResponse)

    suspend fun publishUpdated(response: TestTableResponse)

    suspend fun publishDeleted(response: TestTableResponse)
}
