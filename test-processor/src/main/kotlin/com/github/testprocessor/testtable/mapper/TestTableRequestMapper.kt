package com.github.testprocessor.testtable.mapper

import com.github.template.model.SaveTestTableRequest
import com.github.template.model.TestTableMetadata
import com.github.template.model.TestTableResponse

fun TestTableResponse.toProcessedUpdateRequest(processedDescriptionSuffix: String): SaveTestTableRequest {
    val processedDescription = if (metadata.description.endsWith(processedDescriptionSuffix)) {
        metadata.description
    } else {
        metadata.description + processedDescriptionSuffix
    }
    return SaveTestTableRequest(
        name = name,
        eventDate = eventDate,
        eventTimestamp = eventTimestamp,
        metadata = TestTableMetadata(
            item = metadata.item,
            description = processedDescription
        )
    )
}
