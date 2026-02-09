package com.keeplearning.auth.scim

import com.fasterxml.jackson.annotation.JsonInclude
import com.keeplearning.auth.scim.common.ScimSchemas

// Discovery DTOs (server-only, not shared with auth-sdk)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimServiceProviderConfig(
    val schemas: List<String> = listOf(ScimSchemas.SERVICE_PROVIDER_CONFIG),
    val documentationUri: String? = null,
    val patch: ScimSupported,
    val bulk: ScimBulkSupported,
    val filter: ScimFilterSupported,
    val changePassword: ScimSupported,
    val sort: ScimSupported,
    val etag: ScimSupported,
    val authenticationSchemes: List<ScimAuthenticationScheme>
)

data class ScimSupported(
    val supported: Boolean
)

data class ScimBulkSupported(
    val supported: Boolean,
    val maxOperations: Int = 0,
    val maxPayloadSize: Int = 0
)

data class ScimFilterSupported(
    val supported: Boolean,
    val maxResults: Int = 200
)

data class ScimAuthenticationScheme(
    val type: String,
    val name: String,
    val description: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimResourceType(
    val schemas: List<String> = listOf(ScimSchemas.RESOURCE_TYPE),
    val id: String,
    val name: String,
    val description: String,
    val endpoint: String,
    val schema: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimSchema(
    val schemas: List<String> = listOf(ScimSchemas.SCHEMA),
    val id: String,
    val name: String,
    val description: String,
    val attributes: List<ScimSchemaAttribute>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimSchemaAttribute(
    val name: String,
    val type: String,
    val multiValued: Boolean = false,
    val required: Boolean = false,
    val mutability: String = "readWrite",
    val returned: String = "default",
    val uniqueness: String = "none",
    val description: String? = null,
    val subAttributes: List<ScimSchemaAttribute>? = null
)
