package com.keeplearning.auth.scim

import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.scim.common.*
import java.time.Instant
import java.util.UUID

object ScimUserMapper {

    fun toScimResource(user: User, baseUrl: String): ScimUserResource {
        val formatted = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .ifBlank { null }

        return ScimUserResource(
            id = user.id?.toString(),
            externalId = user.keycloakUserId.ifBlank { null },
            userName = user.email,
            name = ScimName(
                givenName = user.firstName,
                familyName = user.lastName,
                formatted = formatted ?: user.displayName
            ),
            displayName = user.displayName ?: formatted,
            emails = listOf(ScimEmail(value = user.email)),
            phoneNumbers = user.phone?.let { listOf(ScimPhoneNumber(value = it)) },
            title = user.jobTitle,
            active = user.status == "ACTIVE",
            meta = ScimMeta(
                created = user.createdAt.toString(),
                lastModified = (user.updatedAt ?: user.createdAt).toString(),
                location = "$baseUrl/api/scim/v2/Users/${user.id}"
            )
        )
    }

    fun fromScimResource(resource: ScimUserResource, accountId: UUID, realmId: UUID?): User {
        return User(
            keycloakUserId = resource.externalId ?: "",
            email = resource.userName,
            displayName = resource.displayName,
            firstName = resource.name?.givenName,
            lastName = resource.name?.familyName,
            phone = resource.phoneNumbers?.firstOrNull()?.value,
            jobTitle = resource.title,
            accountId = accountId,
            realmId = realmId,
            status = if (resource.active) "ACTIVE" else "INACTIVE"
        )
    }

    fun applyPatch(user: User, operations: List<ScimPatchOperation>): User {
        var updated = user
        for (op in operations) {
            updated = when (op.op.lowercase()) {
                "replace" -> applyReplace(updated, op)
                "add" -> applyReplace(updated, op) // add behaves like replace for single-valued
                "remove" -> applyRemove(updated, op)
                else -> updated
            }
        }
        return updated.copy(updatedAt = Instant.now())
    }

    private fun applyReplace(user: User, op: ScimPatchOperation): User {
        val value = op.value?.toString() ?: return user
        return when (op.path?.lowercase()) {
            "username" -> user.copy(email = value)
            "displayname" -> user.copy(displayName = value)
            "name.givenname" -> user.copy(firstName = value)
            "name.familyname" -> user.copy(lastName = value)
            "title" -> user.copy(jobTitle = value)
            "active" -> user.copy(status = if (value.toBoolean()) "ACTIVE" else "INACTIVE")
            "externalid" -> user.copy(keycloakUserId = value)
            "phonenumbers" -> user.copy(phone = extractPhoneValue(op.value))
            "phonenumbers[type eq \"work\"].value" -> user.copy(phone = value)
            else -> user
        }
    }

    private fun applyRemove(user: User, op: ScimPatchOperation): User {
        return when (op.path?.lowercase()) {
            "displayname" -> user.copy(displayName = null)
            "name.givenname" -> user.copy(firstName = null)
            "name.familyname" -> user.copy(lastName = null)
            "title" -> user.copy(jobTitle = null)
            "phonenumbers" -> user.copy(phone = null)
            else -> user
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPhoneValue(value: Any?): String? {
        if (value is List<*>) {
            val first = value.firstOrNull()
            if (first is Map<*, *>) {
                return (first as Map<String, Any?>)["value"]?.toString()
            }
        }
        return value?.toString()
    }
}
