package com.keeplearning.auth.scim.common

import java.security.MessageDigest

object ScimChecksumUtil {

    fun computeChecksum(users: List<ScimUserResource>): String {
        val canonicalStrings = users.map { canonicalize(it) }
            .sortedBy { it.substringBefore("|").lowercase() }
        val joined = canonicalStrings.joinToString("\n")
        return sha256Hex(joined)
    }

    fun canonicalize(user: ScimUserResource): String {
        return listOf(
            user.userName,
            user.externalId ?: "",
            user.name?.givenName ?: "",
            user.name?.familyName ?: "",
            user.displayName ?: "",
            user.phoneNumbers?.firstOrNull()?.value ?: "",
            user.title ?: "",
            user.active.toString()
        ).joinToString("|")
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
