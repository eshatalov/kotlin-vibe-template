package com.github.template.testtable.service

import com.github.template.jooq.tables.pojos.TestTable
import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableMetadata
import com.github.template.testtable.mapper.toResponse
import com.github.template.testtable.repository.TestTableRepository
import com.github.template.testtable.stream.publisher.TestTableEventPublisher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.JSONB
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableServiceTest {

    private val repository = mockk<TestTableRepository>()
    private val eventPublisher = mockk<TestTableEventPublisher>(relaxed = true)
    private val service = TestTableService(repository, eventPublisher)

    @Test
    fun `findAll should return all items as responses`(): Unit = runBlocking {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadataJson = JSONB.valueOf("{\"item\":\"Test Item\",\"description\":\"Test description\"}")

        val id1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val id2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val testTable1 = TestTable(
            id = id1,
            name = "Item 1",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = metadataJson,
            createdAt = now,
            updatedAt = now
        )
        val testTable2 = TestTable(
            id = id2,
            name = "Item 2",
            eventDate = LocalDate.of(2024, 2, 20),
            eventTimestamp = OffsetDateTime.parse("2024-02-20T15:45:00+00:00"),
            metadata = JSONB.valueOf("{\"item\":\"Item 2\",\"description\":\"Description 2\"}"),
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.findAll() } returns flowOf(testTable1, testTable2)

        val result = service.findAll().toList()

        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo(id1)
        assertThat(result[0].name).isEqualTo("Item 1")
        assertThat(result[0].eventDate).isEqualTo(eventDate)
        assertThat(result[0].eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(result[0].metadata.item).isEqualTo("Test Item")
        assertThat(result[1].id).isEqualTo(id2)
        assertThat(result[1].name).isEqualTo("Item 2")
    }

    @Test
    fun `findById should return response when item exists`(): Unit = runBlocking {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadataJson = JSONB.valueOf("{\"item\":\"Test Item\",\"description\":\"Test description\"}")

        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val testTable = TestTable(
            id = id,
            name = "Test Item",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = metadataJson,
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.findById(id) } returns testTable

        val result = service.findById(id)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.name).isEqualTo("Test Item")
        assertThat(result.eventDate).isEqualTo(eventDate)
        assertThat(result.eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(result.metadata.item).isEqualTo("Test Item")
        assertThat(result.metadata.description).isEqualTo("Test description")
    }

    @Test
    fun `findById should throw NotFoundException when item does not exist`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        coEvery { repository.findById(id) } returns null

        assertThatThrownBy { runBlocking { service.findById(id) } }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("TestTable with id $id not found")
    }

    @Test
    fun `create should insert and return response`(): Unit = runBlocking {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val metadata = TestTableMetadata(item = "New Item", description = "New description")
        val request = SaveTestTableRequest(
            name = "New Item",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = metadata
        )
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val inserted = TestTable(
            id = id,
            name = "New Item",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = JSONB.valueOf("{\"item\":\"New Item\",\"description\":\"New description\"}"),
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.insert("New Item", eventDate, eventTimestamp, metadata) } returns inserted

        val result = service.create(request)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.name).isEqualTo("New Item")
        assertThat(result.eventDate).isEqualTo(eventDate)
        assertThat(result.eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(result.metadata.item).isEqualTo("New Item")
        coVerify { repository.insert("New Item", eventDate, eventTimestamp, metadata) }
        coVerify { eventPublisher.publishCreated(result) }
    }

    @Test
    fun `update should update and return response when item exists`(): Unit = runBlocking {
        val now = OffsetDateTime.now()
        val eventDate = LocalDate.of(2024, 2, 20)
        val eventTimestamp = OffsetDateTime.parse("2024-02-20T15:45:00+00:00")
        val metadata = TestTableMetadata(item = "Updated Item", description = "Updated description")
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val request = SaveTestTableRequest(
            name = "Updated Name",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = metadata
        )
        val updated = TestTable(
            id = id,
            name = "Updated Name",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = JSONB.valueOf("{\"item\":\"Updated Item\",\"description\":\"Updated description\"}"),
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.update(id, "Updated Name", eventDate, eventTimestamp, metadata) } returns updated

        val result = service.update(id, request)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.name).isEqualTo("Updated Name")
        assertThat(result.eventDate).isEqualTo(eventDate)
        assertThat(result.eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(result.metadata.item).isEqualTo("Updated Item")
        coVerify { repository.update(id, "Updated Name", eventDate, eventTimestamp, metadata) }
        coVerify { eventPublisher.publishUpdated(result) }
    }

    @Test
    fun `update should throw NotFoundException when item does not exist`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        val request = SaveTestTableRequest(
            name = "Updated Name",
            eventDate = LocalDate.of(2024, 1, 1),
            eventTimestamp = OffsetDateTime.parse("2024-01-01T00:00:00+00:00"),
            metadata = TestTableMetadata(item = "Test", description = "Test description")
        )
        coEvery {
            repository.update(
                id,
                "Updated Name",
                LocalDate.of(2024, 1, 1),
                OffsetDateTime.parse("2024-01-01T00:00:00+00:00"),
                TestTableMetadata(item = "Test", description = "Test description")
            )
        } returns null

        assertThatThrownBy { runBlocking { service.update(id, request) } }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("TestTable with id $id not found")
        coVerify(exactly = 0) { eventPublisher.publishUpdated(any()) }
    }

    @Test
    fun `delete should delete when item exists`(): Unit = runBlocking {
        val now = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val existing = TestTable(
            id = id,
            name = "Test Item",
            eventDate = LocalDate.of(2024, 1, 15),
            eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00"),
            metadata = JSONB.valueOf("{\"item\":\"Test Item\",\"description\":\"Test description\"}"),
            createdAt = now,
            updatedAt = now
        )
        coEvery { repository.findById(id) } returns existing
        coEvery { repository.deleteById(id) } returns true

        service.delete(id)

        coVerify { repository.findById(id) }
        coVerify { repository.deleteById(id) }
        coVerify { eventPublisher.publishDeleted(existing.toResponse()) }
    }

    @Test
    fun `delete should throw NotFoundException when item does not exist`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        coEvery { repository.findById(id) } returns null

        assertThatThrownBy { runBlocking { service.delete(id) } }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("TestTable with id $id not found")
        coVerify(exactly = 0) { repository.deleteById(any()) }
        coVerify(exactly = 0) { eventPublisher.publishDeleted(any()) }
    }
}
