package com.github.template.testtable.stream

import com.github.template.client.model.TestTableResponse

data class TestTableStreamMessage(
    val eventType: TestTableEventType,
    val response: TestTableResponse
)
