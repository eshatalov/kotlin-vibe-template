package com.github.template.testtable.repository

import com.github.database.rider.core.api.dataset.DataSet
import com.github.database.rider.core.api.dataset.ExpectedDataSet
import com.github.template.AbstractContextTest
import com.github.template.model.TestTableMetadata
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TestTableRepositoryTest : AbstractContextTest() {

    @Autowired
    private lateinit var repository: TestTableRepository

    @Test
    @DataSet
    fun `should insert and find by id`(): Unit = runBlocking {
        val metadata = TestTableMetadata(item = "Test Item", description = "Test description")
        val eventDate = LocalDate.of(2024, 1, 15)
        val eventTimestamp = OffsetDateTime.parse("2024-01-15T10:30:00+00:00")
        val inserted = repository.insert(
            name = "Test Item",
            eventDate = eventDate,
            eventTimestamp = eventTimestamp,
            metadata = metadata
        )

        assertThat(inserted.id).isNotNull()
        assertThat(inserted.name).isEqualTo("Test Item")
        assertThat(inserted.eventDate).isEqualTo(eventDate)
        assertThat(inserted.eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(inserted.metadata).isNotNull()
        assertThat(inserted.createdAt).isNotNull()
        assertThat(inserted.updatedAt).isNotNull()

        val found = repository.findById(inserted.id)
        assertThat(found).isNotNull
        assertThat(found?.name).isEqualTo("Test Item")
        assertThat(found?.eventDate).isEqualTo(eventDate)
        assertThat(found?.eventTimestamp).isEqualTo(eventTimestamp)
        assertThat(found?.updatedAt).isNotNull()
    }

    @Test
    @DataSet("datasets/test_table/test_table.yml", useSequenceFiltering = false)
    fun `should find all items`(): Unit = runBlocking {
        val allItems = repository.findAll().toList()
        assertThat(allItems).hasSize(2)
        assertThat(allItems.map { it.name }).containsExactlyInAnyOrder("Test Item 1", "Test Item 2")
    }

    @Test
    @DataSet("datasets/test_table/single_test_table.yml", useSequenceFiltering = false)
    fun `should update item`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val originalUpdatedAt = OffsetDateTime.parse("2024-01-01T00:00:00+00:00")

        val newMetadata = TestTableMetadata(item = "Updated Item", description = "Updated description")
        val newEventDate = LocalDate.of(2024, 2, 20)
        val newEventTimestamp = OffsetDateTime.parse("2024-02-20T15:45:00+00:00")
        val updated = repository.update(
            id = id,
            name = "Updated Name",
            eventDate = newEventDate,
            eventTimestamp = newEventTimestamp,
            metadata = newMetadata
        )

        assertThat(updated).isNotNull
        assertThat(updated?.name).isEqualTo("Updated Name")
        assertThat(updated?.eventDate).isEqualTo(newEventDate)
        assertThat(updated?.eventTimestamp).isEqualTo(newEventTimestamp)
        assertThat(updated?.id).isEqualTo(id)
        assertThat(updated?.updatedAt).isNotNull()
        assertThat(updated?.updatedAt).isNotEqualTo(originalUpdatedAt)
    }

    @Test
    @DataSet
    fun `should return null when updating non-existent item`(): Unit = runBlocking {
        val nonExistentId = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        val updated = repository.update(
            id = nonExistentId,
            name = "Updated Name",
            eventDate = LocalDate.of(2024, 1, 1),
            eventTimestamp = OffsetDateTime.parse("2024-01-01T00:00:00+00:00"),
            metadata = TestTableMetadata(item = "Test", description = "Test description")
        )

        assertThat(updated).isNull()
    }

    @Test
    @DataSet("datasets/test_table/test_table.yml", useSequenceFiltering = false)
    @ExpectedDataSet("datasets/test_table/expected/test_table_after_delete.yml")
    fun `should delete item by id`(): Unit = runBlocking {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val deleted = repository.deleteById(id)

        assertThat(deleted).isTrue()
    }

    @Test
    @DataSet
    fun `should return false when deleting non-existent item`(): Unit = runBlocking {
        val nonExistentId = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        val deleted = repository.deleteById(nonExistentId)

        assertThat(deleted).isFalse()
    }

    @Test
    @DataSet
    fun `should return null when finding non-existent item`(): Unit = runBlocking {
        val nonExistentId = UUID.fromString("550e8400-e29b-41d4-a716-446655449999")
        val found = repository.findById(nonExistentId)

        assertThat(found).isNull()
    }
}
