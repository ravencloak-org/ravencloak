package com.keeplearning.auth.scim

import com.keeplearning.auth.domain.entity.User
import com.keeplearning.auth.scim.common.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScimUserMapperTest {

    private val accountId = UUID.randomUUID()
    private val realmId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val baseUrl = "https://auth.example.com"

    private val user = User(
        id = userId,
        keycloakUserId = "kc-123",
        email = "john@example.com",
        displayName = "John Doe",
        firstName = "John",
        lastName = "Doe",
        phone = "+1-555-0100",
        jobTitle = "Engineer",
        department = "Engineering",
        accountId = accountId,
        realmId = realmId,
        status = "ACTIVE",
        createdAt = Instant.parse("2024-01-15T10:30:00Z"),
        updatedAt = Instant.parse("2024-06-20T14:22:00Z")
    )

    @Test
    fun `toScimResource maps all fields correctly`() {
        val resource = ScimUserMapper.toScimResource(user, baseUrl)

        assertEquals(userId.toString(), resource.id)
        assertEquals("kc-123", resource.externalId)
        assertEquals("john@example.com", resource.userName)
        assertEquals("John", resource.name?.givenName)
        assertEquals("Doe", resource.name?.familyName)
        assertEquals("John Doe", resource.name?.formatted)
        assertEquals("John Doe", resource.displayName)
        assertEquals(1, resource.emails?.size)
        assertEquals("john@example.com", resource.emails?.first()?.value)
        assertTrue(resource.emails?.first()?.primary == true)
        assertEquals(1, resource.phoneNumbers?.size)
        assertEquals("+1-555-0100", resource.phoneNumbers?.first()?.value)
        assertEquals("Engineer", resource.title)
        assertTrue(resource.active)
        assertEquals("User", resource.meta?.resourceType)
        assertEquals("2024-01-15T10:30:00Z", resource.meta?.created)
        assertEquals("2024-06-20T14:22:00Z", resource.meta?.lastModified)
        assertEquals("$baseUrl/api/scim/v2/Users/$userId", resource.meta?.location)
    }

    @Test
    fun `toScimResource handles inactive user`() {
        val inactive = user.copy(status = "INACTIVE")
        val resource = ScimUserMapper.toScimResource(inactive, baseUrl)

        assertEquals(false, resource.active)
    }

    @Test
    fun `toScimResource handles null optional fields`() {
        val minimal = user.copy(
            displayName = null,
            firstName = null,
            lastName = null,
            phone = null,
            jobTitle = null,
            updatedAt = null
        )
        val resource = ScimUserMapper.toScimResource(minimal, baseUrl)

        assertNull(resource.displayName)
        assertNull(resource.name?.givenName)
        assertNull(resource.name?.familyName)
        assertNull(resource.phoneNumbers)
        assertNull(resource.title)
        // lastModified falls back to createdAt when updatedAt is null
        assertEquals("2024-01-15T10:30:00Z", resource.meta?.lastModified)
    }

    @Test
    fun `toScimResource handles empty keycloakUserId`() {
        val noKcId = user.copy(keycloakUserId = "")
        val resource = ScimUserMapper.toScimResource(noKcId, baseUrl)

        assertNull(resource.externalId)
    }

    @Test
    fun `fromScimResource creates User entity from SCIM resource`() {
        val resource = ScimUserResource(
            userName = "jane@example.com",
            externalId = "kc-456",
            name = ScimName(givenName = "Jane", familyName = "Smith"),
            displayName = "Jane Smith",
            phoneNumbers = listOf(ScimPhoneNumber(value = "+1-555-0200")),
            title = "Manager",
            active = true
        )

        val result = ScimUserMapper.fromScimResource(resource, accountId, realmId)

        assertEquals("jane@example.com", result.email)
        assertEquals("kc-456", result.keycloakUserId)
        assertEquals("Jane", result.firstName)
        assertEquals("Smith", result.lastName)
        assertEquals("Jane Smith", result.displayName)
        assertEquals("+1-555-0200", result.phone)
        assertEquals("Manager", result.jobTitle)
        assertEquals(accountId, result.accountId)
        assertEquals(realmId, result.realmId)
        assertEquals("ACTIVE", result.status)
    }

    @Test
    fun `fromScimResource maps inactive to INACTIVE status`() {
        val resource = ScimUserResource(
            userName = "test@example.com",
            active = false
        )

        val result = ScimUserMapper.fromScimResource(resource, accountId, realmId)
        assertEquals("INACTIVE", result.status)
    }

    @Test
    fun `fromScimResource handles null externalId`() {
        val resource = ScimUserResource(userName = "test@example.com")

        val result = ScimUserMapper.fromScimResource(resource, accountId, realmId)
        assertEquals("", result.keycloakUserId)
    }

    @Test
    fun `applyPatch replace operations`() {
        val ops = listOf(
            ScimPatchOperation(op = "replace", path = "displayName", value = "Jonathan Doe"),
            ScimPatchOperation(op = "replace", path = "title", value = "Senior Engineer"),
            ScimPatchOperation(op = "replace", path = "active", value = "false")
        )

        val result = ScimUserMapper.applyPatch(user, ops)

        assertEquals("Jonathan Doe", result.displayName)
        assertEquals("Senior Engineer", result.jobTitle)
        assertEquals("INACTIVE", result.status)
    }

    @Test
    fun `applyPatch add operations behave like replace`() {
        val ops = listOf(
            ScimPatchOperation(op = "add", path = "name.givenName", value = "Jonathan"),
            ScimPatchOperation(op = "add", path = "name.familyName", value = "Smith")
        )

        val result = ScimUserMapper.applyPatch(user, ops)

        assertEquals("Jonathan", result.firstName)
        assertEquals("Smith", result.lastName)
    }

    @Test
    fun `applyPatch remove operations clear fields`() {
        val ops = listOf(
            ScimPatchOperation(op = "remove", path = "displayName"),
            ScimPatchOperation(op = "remove", path = "title"),
            ScimPatchOperation(op = "remove", path = "phoneNumbers")
        )

        val result = ScimUserMapper.applyPatch(user, ops)

        assertNull(result.displayName)
        assertNull(result.jobTitle)
        assertNull(result.phone)
    }

    @Test
    fun `applyPatch sets updatedAt timestamp`() {
        val before = Instant.now()
        val ops = listOf(
            ScimPatchOperation(op = "replace", path = "displayName", value = "Updated")
        )

        val result = ScimUserMapper.applyPatch(user, ops)

        assertTrue(result.updatedAt != null)
        assertTrue(!result.updatedAt!!.isBefore(before))
    }

    @Test
    fun `applyPatch with unknown path is a no-op`() {
        val ops = listOf(
            ScimPatchOperation(op = "replace", path = "unknownField", value = "something")
        )

        val result = ScimUserMapper.applyPatch(user, ops)

        assertEquals(user.email, result.email)
        assertEquals(user.displayName, result.displayName)
    }

    @Test
    fun `applyPatch replace phoneNumbers with list value`() {
        val ops = listOf(
            ScimPatchOperation(
                op = "replace",
                path = "phoneNumbers",
                value = listOf(mapOf("value" to "+1-999-0000", "type" to "work"))
            )
        )

        val result = ScimUserMapper.applyPatch(user, ops)
        assertEquals("+1-999-0000", result.phone)
    }
}
