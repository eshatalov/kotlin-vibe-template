package com.github.testprocessor.testtable.client

import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableResponse
import java.util.UUID

interface TemplateTestTableClient {

    suspend fun update(id: UUID, request: SaveTestTableRequest): TestTableResponse
}
