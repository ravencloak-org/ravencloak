package com.keeplearning.auth.scim.common

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimErrorResponse(
    val schemas: List<String> = listOf(ScimSchemas.ERROR),
    val status: String,
    val scimType: String? = null,
    val detail: String? = null
)
