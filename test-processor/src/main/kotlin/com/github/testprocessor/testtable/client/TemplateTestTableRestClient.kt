package com.github.testprocessor.testtable.client

import com.github.template.client.TestTableApi
import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TemplateTestTableRestClient(
    private val testTableApi: TestTableApi
) : TemplateTestTableClient {

    override suspend fun update(id: UUID, request: SaveTestTableRequest): TestTableResponse {
        return try {
            testTableApi.updateTestTable(id, request).awaitSingle()
        } catch (exception: Exception) {
            logger.error("Failed to update template test table id={}", id, exception)
            throw IllegalStateException("Failed to update template test table $id", exception)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TemplateTestTableRestClient::class.java)
    }
}
