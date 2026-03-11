package com.github.template.testtable.handler

import com.github.template.model.SaveTestTableRequest
import com.github.template.testtable.service.TestTableService
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.util.UUID

@Component
class TestTableHandler(
    private val service: TestTableService
) {

    suspend fun findAll(request: ServerRequest): ServerResponse {
        val items = service.findAll()
        return ServerResponse.ok().bodyAndAwait(items)
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = UUID.fromString(request.pathVariable("id"))
        val response = service.findById(id)
        return ServerResponse.ok().bodyValueAndAwait(response)
    }

    suspend fun create(request: ServerRequest): ServerResponse {
        val createRequest = request.bodyToMono(SaveTestTableRequest::class.java).awaitSingle()
        val response = service.create(createRequest)
        return ServerResponse.status(201).bodyValueAndAwait(response)
    }

    suspend fun update(request: ServerRequest): ServerResponse {
        val id = UUID.fromString(request.pathVariable("id"))
        val updateRequest = request.bodyToMono(SaveTestTableRequest::class.java).awaitSingle()
        val response = service.update(id, updateRequest)
        return ServerResponse.ok().bodyValueAndAwait(response)
    }

    suspend fun delete(request: ServerRequest): ServerResponse {
        val id = UUID.fromString(request.pathVariable("id"))
        service.delete(id)
        return ServerResponse.noContent().buildAndAwait()
    }
}
