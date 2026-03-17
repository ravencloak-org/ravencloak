package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.URL
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
class AuditServiceImplTest {

    @MockK
    lateinit var auditRepository: EntityActionLogRepository

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private lateinit var service: AuditServiceImpl

    private val entityId = UUID.randomUUID()
    private val realmId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service = AuditServiceImpl(auditRepository, objectMapper)

        every { auditRepository.save(any()) } answers {
            Mono.just(firstArg<EntityActionLog>().copy(id = UUID.randomUUID()))
        }
    }

    // --- logAction ---

    @Test
    fun `logAction extracts actor info from JWT and saves audit log`() = runTest {
        val jwt = io.mockk.mockk<Jwt>()
        every { jwt.subject } returns "user-123"
        every { jwt.claims } returns mapOf(
            "email" to "test@example.com",
            "name" to "Test User",
            "preferred_username" to "testuser"
        )
        every { jwt.issuer } returns URL("https://auth.example.com/realms/test")

        val actor = io.mockk.mockk<JwtAuthenticationToken>()
        every { actor.token } returns jwt

        val result = service.logAction(
            actor = actor,
            actionType = ActionType.CREATE,
            entityType = EntityType.USER,
            entityId = entityId,
            entityName = "john@example.com",
            realmName = "test-realm",
            realmId = realmId,
            entityKeycloakId = "kc-user-456"
        )

        assertNotNull(result.id)

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertEquals("user-123", saved.captured.actorKeycloakId)
        assertEquals("test@example.com", saved.captured.actorEmail)
        assertEquals("Test User", saved.captured.actorDisplayName)
        assertEquals("https://auth.example.com/realms/test", saved.captured.actorIssuer)
        assertEquals(ActionType.CREATE, saved.captured.actionType)
        assertEquals(EntityType.USER, saved.captured.entityType)
        assertEquals(entityId, saved.captured.entityId)
        assertEquals("john@example.com", saved.captured.entityName)
        assertEquals("test-realm", saved.captured.realmName)
        assertEquals(realmId, saved.captured.realmId)
        assertEquals("kc-user-456", saved.captured.entityKeycloakId)
    }

    @Test
    fun `logAction falls back to preferred_username when name claim is absent`() = runTest {
        val jwt = io.mockk.mockk<Jwt>()
        every { jwt.subject } returns "user-789"
        every { jwt.claims } returns mapOf(
            "email" to "admin@example.com",
            "preferred_username" to "adminuser"
        )
        every { jwt.issuer } returns URL("https://auth.example.com/realms/admin")

        val actor = io.mockk.mockk<JwtAuthenticationToken>()
        every { actor.token } returns jwt

        service.logAction(
            actor = actor,
            actionType = ActionType.DELETE,
            entityType = EntityType.CLIENT,
            entityId = entityId,
            entityName = "test-client",
            realmName = "admin-realm"
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertEquals("adminuser", saved.captured.actorDisplayName)
    }

    @Test
    fun `logAction serializes beforeState and afterState to JSON`() = runTest {
        val jwt = io.mockk.mockk<Jwt>()
        every { jwt.subject } returns "user-123"
        every { jwt.claims } returns mapOf("email" to "test@example.com")
        every { jwt.issuer } returns URL("https://auth.example.com/realms/test")

        val actor = io.mockk.mockk<JwtAuthenticationToken>()
        every { actor.token } returns jwt

        val before = mapOf("name" to "Old Name", "email" to "old@example.com")
        val after = mapOf("name" to "New Name", "email" to "old@example.com")

        service.logAction(
            actor = actor,
            actionType = ActionType.UPDATE,
            entityType = EntityType.USER,
            entityId = entityId,
            entityName = "test-user",
            realmName = "test-realm",
            beforeState = before,
            afterState = after
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertNotNull(saved.captured.beforeState)
        assertNotNull(saved.captured.afterState)

        val beforeJson = saved.captured.beforeState!!.asString()
        assert(beforeJson.contains("Old Name"))

        val afterJson = saved.captured.afterState!!.asString()
        assert(afterJson.contains("New Name"))
    }

    // --- logSystemAction ---

    @Test
    fun `logSystemAction uses SYSTEM as actor with no email or issuer`() = runTest {
        val result = service.logSystemAction(
            actionType = ActionType.CREATE,
            entityType = EntityType.REALM,
            entityId = entityId,
            entityName = "new-realm",
            realmName = "new-realm",
            realmId = realmId
        )

        assertNotNull(result.id)

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertEquals("SYSTEM", saved.captured.actorKeycloakId)
        assertNull(saved.captured.actorEmail)
        assertEquals("System", saved.captured.actorDisplayName)
        assertNull(saved.captured.actorIssuer)
        assertEquals(ActionType.CREATE, saved.captured.actionType)
        assertEquals(EntityType.REALM, saved.captured.entityType)
    }

    @Test
    fun `logSystemAction with no before or after state saves nulls`() = runTest {
        service.logSystemAction(
            actionType = ActionType.DELETE,
            entityType = EntityType.GROUP,
            entityId = entityId,
            entityName = "deleted-group",
            realmName = "test-realm"
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertNull(saved.captured.beforeState)
        assertNull(saved.captured.afterState)
        assertNull(saved.captured.changedFields)
    }

    // --- changedFields computation ---

    @Test
    fun `UPDATE action computes changedFields correctly`() = runTest {
        val jwt = io.mockk.mockk<Jwt>()
        every { jwt.subject } returns "user-123"
        every { jwt.claims } returns mapOf("email" to "test@example.com")
        every { jwt.issuer } returns URL("https://auth.example.com/realms/test")

        val actor = io.mockk.mockk<JwtAuthenticationToken>()
        every { actor.token } returns jwt

        val before = mapOf("name" to "Old", "email" to "same@example.com", "phone" to "111")
        val after = mapOf("name" to "New", "email" to "same@example.com", "title" to "CTO")

        service.logAction(
            actor = actor,
            actionType = ActionType.UPDATE,
            entityType = EntityType.USER,
            entityId = entityId,
            entityName = "test-user",
            realmName = "test-realm",
            beforeState = before,
            afterState = after
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        val changed = saved.captured.changedFields!!.toSet()
        // "name" changed value, "title" is new in after, "phone" was removed
        assert("name" in changed) { "Expected 'name' in changedFields" }
        assert("title" in changed) { "Expected 'title' in changedFields" }
        assert("phone" in changed) { "Expected 'phone' in changedFields" }
        assert("email" !in changed) { "Expected 'email' NOT in changedFields" }
    }

    @Test
    fun `CREATE action does not compute changedFields`() = runTest {
        service.logSystemAction(
            actionType = ActionType.CREATE,
            entityType = EntityType.USER,
            entityId = entityId,
            entityName = "new-user",
            realmName = "test-realm",
            afterState = mapOf("name" to "New User")
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertNull(saved.captured.changedFields)
    }

    @Test
    fun `DELETE action does not compute changedFields`() = runTest {
        service.logSystemAction(
            actionType = ActionType.DELETE,
            entityType = EntityType.USER,
            entityId = entityId,
            entityName = "deleted-user",
            realmName = "test-realm",
            beforeState = mapOf("name" to "Deleted User")
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertNull(saved.captured.changedFields)
    }

    @Test
    fun `UPDATE with identical before and after produces empty changedFields`() = runTest {
        val state = mapOf("name" to "Same", "email" to "same@example.com")

        service.logSystemAction(
            actionType = ActionType.UPDATE,
            entityType = EntityType.CLIENT,
            entityId = entityId,
            entityName = "test-client",
            realmName = "test-realm",
            beforeState = state,
            afterState = state
        )

        val saved = slot<EntityActionLog>()
        verify { auditRepository.save(capture(saved)) }

        assertNotNull(saved.captured.changedFields)
        assertContentEquals(emptyArray(), saved.captured.changedFields)
    }
}
