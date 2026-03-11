package com.github.template.testtable.mapper

import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse
import com.github.template.jooq.tables.pojos.TestTable
import org.jooq.JSONB
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val logger = LoggerFactory.getLogger("com.github.template.testtable.mapper.TestTableMapper")
private val objectMapper = jacksonObjectMapper()

fun TestTable.toResponse(): TestTableResponse {
    return TestTableResponse(
        id = this.id,
        name = this.name,
        eventDate = this.eventDate,
        eventTimestamp = this.eventTimestamp,
        metadata = parseMetadata(this.metadata),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

private fun parseMetadata(jsonb: JSONB): TestTableMetadata {
    return try {
        objectMapper.readValue<TestTableMetadata>(jsonb.data())
    } catch (e: Exception) {
        logger.error("Failed to parse JSONB metadata: ${jsonb.data()}", e)
        throw IllegalStateException("Failed to parse metadata from JSONB", e)
    }
}
