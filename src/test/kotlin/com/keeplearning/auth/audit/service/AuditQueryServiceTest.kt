package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class AuditQueryServiceTest {

    @MockK
    lateinit var auditRepository: EntityActionLogRepository

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private lateinit var service: AuditQueryService

    private val entityId = UUID.randomUUID()
    private val actionId = UUID.randomUUID()

    private fun createLog(
        id: UUID = UUID.randomUUID(),
        actorKeycloakId: String = "user-123",
        actionType: ActionType = ActionType.CREATE,
        entityType: EntityType = EntityType.USER,
        entityId: UUID = this.entityId,
        entityName: String = "test-user",
        realmName: String = "test-realm",
        reverted: Boolean = false,
        createdAt: Instant = Instant.now()
    ) = EntityActionLog(
        id = id,
        actorKeycloakId = actorKeycloakId,
        actorEmail = "test@example.com",
        actorDisplayName = "Test User",
        actorIssuer = "https://auth.example.com/realms/test",
        actionType = actionType,
        entityType = entityType,
        entityId = entityId,
        entityName = entityName,
        realmName = realmName,
        reverted = reverted,
        createdAt = createdAt
    )

    @BeforeEach
    fun setup() {
        service = AuditQueryService(auditRepository, objectMapper)
    }

    // --- getMyActions ---

    @Test
    fun `getMyActions returns paginated results for actor`() = runTest {
        val logs = (1..3).map { createLog(actorKeycloakId = "user-abc") }

        every {
            auditRepository.findByActorKeycloakIdOrderByCreatedAtDesc(eq("user-abc"), any<Pageable>())
        } returns Flux.fromIterable(logs)
        every { auditRepository.countByActorKeycloakId("user-abc") } returns Mono.just(3L)
        every { auditRepository.canRevertAction(any(), any(), any()) } returns Mono.just(true)

        val result = service.getMyActions("user-abc", 0, 10)

        assertEquals(3, result.content.size)
        assertEquals(0, result.page)
        assertEquals(10, result.size)
        assertEquals(3L, result.totalElements)
        assertEquals(1, result.totalPages)
    }

    @Test
    fun `getMyActions computes totalPages correctly`() = runTest {
        val logs = (1..5).map { createLog(actorKeycloakId = "user-abc") }

        every {
            auditRepository.findByActorKeycloakIdOrderByCreatedAtDesc(eq("user-abc"), any<Pageable>())
        } returns Flux.fromIterable(logs)
        every { auditRepository.countByActorKeycloakId("user-abc") } returns Mono.just(12L)
        every { auditRepository.canRevertAction(any(), any(), any()) } returns Mono.just(false)

        val result = service.getMyActions("user-abc", 0, 5)

        assertEquals(12L, result.totalElements)
        assertEquals(3, result.totalPages) // ceil(12/5) = 3
    }

    // --- getRealmActions ---

    @Test
    fun `getRealmActions returns paginated results for realm`() = runTest {
        val logs = (1..2).map { createLog(realmName = "my-realm") }

        every {
            auditRepository.findByRealmNameOrderByCreatedAtDesc(eq("my-realm"), any<Pageable>())
        } returns Flux.fromIterable(logs)
        every { auditRepository.countByRealmName("my-realm") } returns Mono.just(2L)
        every { auditRepository.canRevertAction(any(), any(), any()) } returns Mono.just(true)

        val result = service.getRealmActions("my-realm", 0, 10)

        assertEquals(2, result.content.size)
        assertEquals(2L, result.totalElements)
        assertEquals(1, result.totalPages)
    }

    // --- getAllActions ---

    @Test
    fun `getAllActions returns paginated results for super admin`() = runTest {
        val logs = (1..4).map { createLog() }

        every {
            auditRepository.findAllByOrderByCreatedAtDesc(any<Pageable>())
        } returns Flux.fromIterable(logs)
        every { auditRepository.count() } returns Mono.just(20L)
        every { auditRepository.canRevertAction(any(), any(), any()) } returns Mono.just(false)

        val result = service.getAllActions(0, 10)

        assertEquals(4, result.content.size)
        assertEquals(20L, result.totalElements)
        assertEquals(2, result.totalPages) // ceil(20/10) = 2
    }

    @Test
    fun `getAllActions returns empty when no logs exist`() = runTest {
        every {
            auditRepository.findAllByOrderByCreatedAtDesc(any<Pageable>())
        } returns Flux.empty()
        every { auditRepository.count() } returns Mono.just(0L)

        val result = service.getAllActions(0, 10)

        assertTrue(result.content.isEmpty())
        assertEquals(0L, result.totalElements)
        assertEquals(0, result.totalPages)
    }

    // --- getAction ---

    @Test
    fun `getAction returns log when found`() = runTest {
        val log = createLog(id = actionId)

        every { auditRepository.findById(actionId) } returns Mono.just(log)

        val result = service.getAction(actionId)

        assertNotNull(result)
        assertEquals(actionId, result.id)
    }

    @Test
    fun `getAction returns null when not found`() = runTest {
        every { auditRepository.findById(actionId) } returns Mono.empty()

        val result = service.getAction(actionId)

        assertNull(result)
    }

    // --- canRevert ---

    @Test
    fun `canRevert returns false for already reverted action`() = runTest {
        val revertedLog = createLog(id = actionId, reverted = true)

        every { auditRepository.findById(actionId) } returns Mono.just(revertedLog)

        val result = service.canRevert(actionId)

        assertFalse(result)
    }

    @Test
    fun `canRevert returns true when action is not reverted and no subsequent actions`() = runTest {
        val log = createLog(id = actionId, reverted = false)

        every { auditRepository.findById(actionId) } returns Mono.just(log)
        every {
            auditRepository.canRevertAction(
                eq(EntityType.USER.name),
                eq(entityId),
                any<Instant>()
            )
        } returns Mono.just(true)

        val result = service.canRevert(actionId)

        assertTrue(result)
    }

    @Test
    fun `canRevert returns false when subsequent non-reverted actions exist`() = runTest {
        val log = createLog(id = actionId, reverted = false)

        every { auditRepository.findById(actionId) } returns Mono.just(log)
        every {
            auditRepository.canRevertAction(
                eq(EntityType.USER.name),
                eq(entityId),
                any<Instant>()
            )
        } returns Mono.just(false)

        val result = service.canRevert(actionId)

        assertFalse(result)
    }

    @Test
    fun `canRevert returns false when action is not found`() = runTest {
        every { auditRepository.findById(actionId) } returns Mono.empty()

        val result = service.canRevert(actionId)

        assertFalse(result)
    }
}
