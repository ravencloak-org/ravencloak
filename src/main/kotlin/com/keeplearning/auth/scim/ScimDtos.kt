package com.keeplearning.auth.scim

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

object ScimSchemas {
    const val USER = "urn:ietf:params:scim:schemas:core:2.0:User"
    const val LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse"
    const val ERROR = "urn:ietf:params:scim:api:messages:2.0:Error"
    const val PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp"
    const val SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"
    const val SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema"
    const val RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType"
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimUserResource(
    val schemas: List<String> = listOf(ScimSchemas.USER),
    val id: String? = null,
    val externalId: String? = null,
    val userName: String,
    val name: ScimName? = null,
    val displayName: String? = null,
    val emails: List<ScimEmail>? = null,
    val phoneNumbers: List<ScimPhoneNumber>? = null,
    val title: String? = null,
    val active: Boolean = true,
    val meta: ScimMeta? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimName(
    val givenName: String? = null,
    val familyName: String? = null,
    val formatted: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimEmail(
    val value: String,
    val type: String = "work",
    val primary: Boolean = true
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimPhoneNumber(
    val value: String,
    val type: String = "work"
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimMeta(
    val resourceType: String = "User",
    val created: String? = null,
    val lastModified: String? = null,
    val location: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimListResponse(
    val schemas: List<String> = listOf(ScimSchemas.LIST_RESPONSE),
    val totalResults: Int,
    val startIndex: Int = 1,
    val itemsPerPage: Int,
    @JsonProperty("Resources")
    val resources: List<ScimUserResource>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScimErrorResponse(
    val schemas: List<String> = listOf(ScimSchemas.ERROR),
    val status: String,
    val scimType: String? = null,
    val detail: String? = null
)

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

// Discovery DTOs

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
