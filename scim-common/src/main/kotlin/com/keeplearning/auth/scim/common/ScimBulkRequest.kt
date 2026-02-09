package com.keeplearning.auth.scim.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class ScimBulkRequest(
    val schemas: List<String> = listOf(ScimSchemas.BULK_REQUEST),
    @JsonProperty("Operations")
    val operations: List<ScimBulkOperation>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimBulkOperation(
    val method: String,
    val path: String? = null,
    val bulkId: String? = null,
    val data: ScimUserResource? = null
)

data class ScimBulkResponse(
    val schemas: List<String> = listOf(ScimSchemas.BULK_RESPONSE),
    @JsonProperty("Operations")
    val operations: List<ScimBulkOperationResponse>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimBulkOperationResponse(
    val method: String,
    val bulkId: String? = null,
    val location: String? = null,
    val status: String,
    val response: Any? = null
)
