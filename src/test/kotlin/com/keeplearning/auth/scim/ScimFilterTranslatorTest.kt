package com.keeplearning.auth.scim

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScimFilterTranslatorTest {

    @Test
    fun `eq filter translates to equals`() {
        val result = ScimFilterTranslator.translate("userName eq \"john@example.com\"")

        assertEquals("email = :p0", result.whereClause)
        assertEquals("john@example.com", result.bindings["p0"])
    }

    @Test
    fun `ne filter translates to not equals`() {
        val result = ScimFilterTranslator.translate("userName ne \"admin@example.com\"")

        assertEquals("email != :p0", result.whereClause)
        assertEquals("admin@example.com", result.bindings["p0"])
    }

    @Test
    fun `co filter translates to ILIKE contains`() {
        val result = ScimFilterTranslator.translate("displayName co \"John\"")

        assertEquals("display_name ILIKE :p0", result.whereClause)
        assertEquals("%John%", result.bindings["p0"])
    }

    @Test
    fun `sw filter translates to ILIKE starts with`() {
        val result = ScimFilterTranslator.translate("userName sw \"john\"")

        assertEquals("email ILIKE :p0", result.whereClause)
        assertEquals("john%", result.bindings["p0"])
    }

    @Test
    fun `ew filter translates to ILIKE ends with`() {
        val result = ScimFilterTranslator.translate("userName ew \"@example.com\"")

        assertEquals("email ILIKE :p0", result.whereClause)
        assertEquals("%@example.com", result.bindings["p0"])
    }

    @Test
    fun `gt filter translates to greater than`() {
        val result = ScimFilterTranslator.translate("meta.created gt \"2024-01-01T00:00:00Z\"")

        assertEquals("created_at > :p0", result.whereClause)
        assertEquals("2024-01-01T00:00:00Z", result.bindings["p0"])
    }

    @Test
    fun `ge filter translates to greater or equal`() {
        val result = ScimFilterTranslator.translate("meta.created ge \"2024-01-01T00:00:00Z\"")

        assertEquals("created_at >= :p0", result.whereClause)
    }

    @Test
    fun `lt filter translates to less than`() {
        val result = ScimFilterTranslator.translate("meta.lastModified lt \"2024-12-31T23:59:59Z\"")

        assertEquals("updated_at < :p0", result.whereClause)
    }

    @Test
    fun `le filter translates to less or equal`() {
        val result = ScimFilterTranslator.translate("meta.lastModified le \"2024-12-31T23:59:59Z\"")

        assertEquals("updated_at <= :p0", result.whereClause)
    }

    @Test
    fun `active eq true maps to status ACTIVE`() {
        val result = ScimFilterTranslator.translate("active eq \"true\"")

        assertEquals("status = :p0", result.whereClause)
        assertEquals("ACTIVE", result.bindings["p0"])
    }

    @Test
    fun `active eq false maps to status INACTIVE`() {
        val result = ScimFilterTranslator.translate("active eq \"false\"")

        assertEquals("status = :p0", result.whereClause)
        assertEquals("INACTIVE", result.bindings["p0"])
    }

    @Test
    fun `and filter combines with AND`() {
        val result = ScimFilterTranslator.translate("active eq \"true\" and userName sw \"john\"")

        assertTrue(result.whereClause.contains("AND"))
        assertTrue(result.whereClause.contains("status = :p0"))
        assertTrue(result.whereClause.contains("email ILIKE :p1"))
        assertEquals("ACTIVE", result.bindings["p0"])
        assertEquals("john%", result.bindings["p1"])
    }

    @Test
    fun `or filter combines with OR`() {
        val result = ScimFilterTranslator.translate("userName eq \"a@b.com\" or userName eq \"c@d.com\"")

        assertTrue(result.whereClause.contains("OR"))
        assertEquals("a@b.com", result.bindings["p0"])
        assertEquals("c@d.com", result.bindings["p1"])
    }

    @Test
    fun `name givenName maps to first_name column`() {
        val result = ScimFilterTranslator.translate("name.givenName eq \"John\"")

        assertEquals("first_name = :p0", result.whereClause)
        assertEquals("John", result.bindings["p0"])
    }

    @Test
    fun `name familyName maps to last_name column`() {
        val result = ScimFilterTranslator.translate("name.familyName co \"Doe\"")

        assertEquals("last_name ILIKE :p0", result.whereClause)
        assertEquals("%Doe%", result.bindings["p0"])
    }

    @Test
    fun `title maps to job_title column`() {
        val result = ScimFilterTranslator.translate("title eq \"Engineer\"")

        assertEquals("job_title = :p0", result.whereClause)
    }

    @Test
    fun `externalId maps to keycloak_user_id column`() {
        val result = ScimFilterTranslator.translate("externalId eq \"kc-123\"")

        assertEquals("keycloak_user_id = :p0", result.whereClause)
    }

    @Test
    fun `unsupported attribute throws ScimException`() {
        val ex = assertThrows<ScimException> {
            ScimFilterTranslator.translate("unsupportedAttr eq \"value\"")
        }

        assertEquals(400, ex.status)
        assertEquals("invalidFilter", ex.scimType)
        assertTrue(ex.detail!!.contains("unsupportedAttr"))
    }

    @Test
    fun `complex AND-OR filter generates correct SQL`() {
        val result = ScimFilterTranslator.translate(
            "active eq \"true\" and (userName sw \"john\" or userName sw \"jane\")"
        )

        assertTrue(result.whereClause.contains("AND"))
        assertTrue(result.whereClause.contains("OR"))
        assertEquals(3, result.bindings.size)
    }

    @Test
    fun `emails value maps to email column`() {
        val result = ScimFilterTranslator.translate("emails.value eq \"john@example.com\"")

        assertEquals("email = :p0", result.whereClause)
    }

    @Test
    fun `phoneNumbers value maps to phone column`() {
        val result = ScimFilterTranslator.translate("phoneNumbers.value co \"555\"")

        assertEquals("phone ILIKE :p0", result.whereClause)
        assertEquals("%555%", result.bindings["p0"])
    }
}
