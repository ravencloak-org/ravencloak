package com.keeplearning.auth.scim.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimListResponse(
    val schemas: List<String> = listOf(ScimSchemas.LIST_RESPONSE),
    val totalResults: Int,
    val startIndex: Int = 1,
    val itemsPerPage: Int,
    @JsonProperty("Resources")
    val resources: List<ScimUserResource>
)
