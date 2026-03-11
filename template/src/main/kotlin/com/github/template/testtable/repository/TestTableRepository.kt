package com.github.template.testtable.repository

import com.github.template.jooq.tables.pojos.TestTable
import com.github.template.jooq.tables.references.TEST_TABLE
import com.github.template.model.TestTableMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.springframework.stereotype.Repository
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class TestTableRepository(
    private val dsl: DSLContext
) {

    private val objectMapper = jacksonObjectMapper()

    fun findAll(): Flow<TestTable> {
        return dsl
            .selectFrom(TEST_TABLE)
            .asFlow()
            .map { it.into(TestTable::class.java) }
    }

    suspend fun findById(id: UUID): TestTable? {
        return dsl
            .selectFrom(TEST_TABLE)
            .where(TEST_TABLE.ID.eq(id))
            .awaitFirstOrNull()
            ?.into(TestTable::class.java)
    }

    suspend fun insert(
        name: String,
        eventDate: LocalDate,
        eventTimestamp: OffsetDateTime,
        metadata: TestTableMetadata
    ): TestTable {
        val now = OffsetDateTime.now()
        val id = UUID.randomUUID()
        return dsl.transactionCoroutine { config ->
            config.dsl()
                .insertInto(TEST_TABLE)
                .set(TEST_TABLE.ID, id)
                .set(TEST_TABLE.NAME, name)
                .set(TEST_TABLE.EVENT_DATE, eventDate)
                .set(TEST_TABLE.EVENT_TIMESTAMP, eventTimestamp)
                .set(TEST_TABLE.METADATA, JSONB.valueOf(objectMapper.writeValueAsString(metadata)))
                .set(TEST_TABLE.CREATED_AT, now)
                .set(TEST_TABLE.UPDATED_AT, now)
                .returning()
                .awaitFirst()
                .into(TestTable::class.java)
        }
    }

    suspend fun update(
        id: UUID,
        name: String,
        eventDate: LocalDate,
        eventTimestamp: OffsetDateTime,
        metadata: TestTableMetadata
    ): TestTable? {
        return dsl.transactionCoroutine { config ->
            config.dsl()
                .update(TEST_TABLE)
                .set(TEST_TABLE.NAME, name)
                .set(TEST_TABLE.EVENT_DATE, eventDate)
                .set(TEST_TABLE.EVENT_TIMESTAMP, eventTimestamp)
                .set(TEST_TABLE.METADATA, JSONB.valueOf(objectMapper.writeValueAsString(metadata)))
                .set(TEST_TABLE.UPDATED_AT, OffsetDateTime.now())
                .where(TEST_TABLE.ID.eq(id))
                .returning()
                .awaitFirstOrNull()
                ?.into(TestTable::class.java)
        }
    }

    suspend fun deleteById(id: UUID): Boolean {
        return dsl.transactionCoroutine { config ->
            config.dsl()
                .deleteFrom(TEST_TABLE)
                .where(TEST_TABLE.ID.eq(id))
                .returning()
                .awaitFirstOrNull() != null
        }
    }

}
