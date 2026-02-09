package com.keeplearning.auth.scim.common

import com.fasterxml.jackson.annotation.JsonProperty

data class ScimPatchRequest(
    val schemas: List<String> = listOf(ScimSchemas.PATCH_OP),
    @JsonProperty("Operations")
    val operations: List<ScimPatchOperation>
)

data class ScimPatchOperation(
    val op: String,
    val path: String? = null,
    val value: Any? = null
)
