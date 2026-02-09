package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.ScimSchemas
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scim/v2")
class ScimDiscoveryController {

    @GetMapping("/ServiceProviderConfig", version = "1.0+")
    suspend fun getServiceProviderConfig(): ScimServiceProviderConfig {
        return ScimServiceProviderConfig(
            documentationUri = "https://datatracker.ietf.org/doc/html/rfc7644",
            patch = ScimSupported(supported = true),
            bulk = ScimBulkSupported(supported = false),
            filter = ScimFilterSupported(supported = true, maxResults = 200),
            changePassword = ScimSupported(supported = false),
            sort = ScimSupported(supported = false),
            etag = ScimSupported(supported = false),
            authenticationSchemes = listOf(
                ScimAuthenticationScheme(
                    type = "oauthbearertoken",
                    name = "OAuth Bearer Token",
                    description = "Authentication scheme using the OAuth 2.0 Bearer Token"
                )
            )
        )
    }

    @GetMapping("/ResourceTypes", version = "1.0+")
    suspend fun getResourceTypes(): List<ScimResourceType> {
        return listOf(
            ScimResourceType(
                id = "User",
                name = "User",
                description = "User Account",
                endpoint = "/Users",
                schema = ScimSchemas.USER
            )
        )
    }

    @GetMapping("/Schemas", version = "1.0+")
    suspend fun getSchemas(): List<ScimSchema> {
        return listOf(userSchema())
    }

    @GetMapping("/Schemas/${ScimSchemas.USER}", version = "1.0+")
    suspend fun getUserSchema(): ScimSchema {
        return userSchema()
    }

    private fun userSchema() = ScimSchema(
        id = ScimSchemas.USER,
        name = "User",
        description = "User Account",
        attributes = listOf(
            ScimSchemaAttribute(
                name = "userName",
                type = "string",
                required = true,
                uniqueness = "server",
                description = "Unique identifier for the User, typically the email address"
            ),
            ScimSchemaAttribute(
                name = "name",
                type = "complex",
                description = "The components of the user's real name",
                subAttributes = listOf(
                    ScimSchemaAttribute(name = "givenName", type = "string", description = "Given name"),
                    ScimSchemaAttribute(name = "familyName", type = "string", description = "Family name"),
                    ScimSchemaAttribute(name = "formatted", type = "string", mutability = "readOnly", description = "Full name formatted for display")
                )
            ),
            ScimSchemaAttribute(name = "displayName", type = "string", description = "Name displayed to the user"),
            ScimSchemaAttribute(
                name = "emails",
                type = "complex",
                multiValued = true,
                description = "Email addresses for the user",
                subAttributes = listOf(
                    ScimSchemaAttribute(name = "value", type = "string", description = "Email address"),
                    ScimSchemaAttribute(name = "type", type = "string", description = "Type of email (e.g., work)"),
                    ScimSchemaAttribute(name = "primary", type = "boolean", description = "Whether this is the primary email")
                )
            ),
            ScimSchemaAttribute(
                name = "phoneNumbers",
                type = "complex",
                multiValued = true,
                description = "Phone numbers for the user",
                subAttributes = listOf(
                    ScimSchemaAttribute(name = "value", type = "string", description = "Phone number"),
                    ScimSchemaAttribute(name = "type", type = "string", description = "Type of phone number (e.g., work)")
                )
            ),
            ScimSchemaAttribute(name = "title", type = "string", description = "User's title / job title"),
            ScimSchemaAttribute(name = "active", type = "boolean", description = "Whether the user account is active"),
            ScimSchemaAttribute(name = "externalId", type = "string", description = "External identifier (Keycloak user ID)")
        )
    )
}
