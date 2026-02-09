package com.keeplearning.auth.scim.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ScimChecksumUtilTest {

    @Test
    fun `same users produce same checksum`() {
        val users = listOf(
            ScimUserResource(
                userName = "alice@example.com",
                externalId = "ext-1",
                name = ScimName(givenName = "Alice", familyName = "Smith"),
                displayName = "Alice Smith",
                active = true
            ),
            ScimUserResource(
                userName = "bob@example.com",
                name = ScimName(givenName = "Bob", familyName = "Jones"),
                displayName = "Bob Jones",
                active = true
            )
        )

        val checksum1 = ScimChecksumUtil.computeChecksum(users)
        val checksum2 = ScimChecksumUtil.computeChecksum(users)

        assertEquals(checksum1, checksum2)
    }

    @Test
    fun `reordered users produce same checksum`() {
        val alice = ScimUserResource(
            userName = "alice@example.com",
            name = ScimName(givenName = "Alice", familyName = "Smith"),
            displayName = "Alice Smith",
            active = true
        )
        val bob = ScimUserResource(
            userName = "bob@example.com",
            name = ScimName(givenName = "Bob", familyName = "Jones"),
            displayName = "Bob Jones",
            active = true
        )

        val checksum1 = ScimChecksumUtil.computeChecksum(listOf(alice, bob))
        val checksum2 = ScimChecksumUtil.computeChecksum(listOf(bob, alice))

        assertEquals(checksum1, checksum2)
    }

    @Test
    fun `different users produce different checksum`() {
        val users1 = listOf(
            ScimUserResource(userName = "alice@example.com", active = true)
        )
        val users2 = listOf(
            ScimUserResource(userName = "bob@example.com", active = true)
        )

        val checksum1 = ScimChecksumUtil.computeChecksum(users1)
        val checksum2 = ScimChecksumUtil.computeChecksum(users2)

        assertNotEquals(checksum1, checksum2)
    }

    @Test
    fun `changing a field changes the checksum`() {
        val users1 = listOf(
            ScimUserResource(
                userName = "alice@example.com",
                name = ScimName(givenName = "Alice"),
                active = true
            )
        )
        val users2 = listOf(
            ScimUserResource(
                userName = "alice@example.com",
                name = ScimName(givenName = "Alicia"),
                active = true
            )
        )

        val checksum1 = ScimChecksumUtil.computeChecksum(users1)
        val checksum2 = ScimChecksumUtil.computeChecksum(users2)

        assertNotEquals(checksum1, checksum2)
    }

    @Test
    fun `empty list produces consistent checksum`() {
        val checksum1 = ScimChecksumUtil.computeChecksum(emptyList())
        val checksum2 = ScimChecksumUtil.computeChecksum(emptyList())

        assertEquals(checksum1, checksum2)
    }

    @Test
    fun `canonicalize handles null fields`() {
        val user = ScimUserResource(userName = "test@example.com", active = true)
        val canonical = ScimChecksumUtil.canonicalize(user)

        assertEquals("test@example.com|||||||true", canonical)
    }

    @Test
    fun `canonicalize includes all fields`() {
        val user = ScimUserResource(
            userName = "test@example.com",
            externalId = "ext-1",
            name = ScimName(givenName = "Test", familyName = "User"),
            displayName = "Test User",
            phoneNumbers = listOf(ScimPhoneNumber(value = "+1-555-0100")),
            title = "Engineer",
            active = false
        )
        val canonical = ScimChecksumUtil.canonicalize(user)

        assertEquals("test@example.com|ext-1|Test|User|Test User|+1-555-0100|Engineer|false", canonical)
    }
}
