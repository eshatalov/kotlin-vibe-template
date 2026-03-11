package com.github.template.testtable.service

import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableResponse
import com.github.template.testtable.mapper.toResponse
import com.github.template.testtable.repository.TestTableRepository
import com.github.template.testtable.stream.publisher.TestTableEventPublisher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TestTableService(
    private val repository: TestTableRepository,
    private val eventPublisher: TestTableEventPublisher
) {

    @Transactional(readOnly = true)
    fun findAll(): Flow<TestTableResponse> {
        return repository.findAll().map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    suspend fun findById(id: UUID): TestTableResponse {
        return repository.findById(id)
            ?.toResponse()
            ?: throw NotFoundException("TestTable with id $id not found")
    }

    @Transactional
    suspend fun create(request: SaveTestTableRequest): TestTableResponse {
        val response = repository.insert(
            name = request.name,
            eventDate = request.eventDate,
            eventTimestamp = request.eventTimestamp,
            metadata = request.metadata
        ).toResponse()
        eventPublisher.publishCreated(response)
        return response
    }

    @Transactional
    suspend fun update(id: UUID, request: SaveTestTableRequest): TestTableResponse {
        val response = repository.update(
            id = id,
            name = request.name,
            eventDate = request.eventDate,
            eventTimestamp = request.eventTimestamp,
            metadata = request.metadata
        )
            ?.toResponse()
            ?: throw NotFoundException("TestTable with id $id not found")
        eventPublisher.publishUpdated(response)
        return response
    }

    @Transactional
    suspend fun delete(id: UUID) {
        val response = repository.findById(id)
            ?.toResponse()
            ?: throw NotFoundException("TestTable with id $id not found")
        if (!repository.deleteById(id)) {
            throw NotFoundException("TestTable with id $id not found")
        }
        eventPublisher.publishDeleted(response)
    }
}
