package com.keeplearning.auth.scim.common

import com.fasterxml.jackson.annotation.JsonInclude

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
