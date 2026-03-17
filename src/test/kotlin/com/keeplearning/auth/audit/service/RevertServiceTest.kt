package com.keeplearning.auth.audit.service

import com.keeplearning.auth.audit.domain.ActionType
import com.keeplearning.auth.audit.domain.EntityActionLog
import com.keeplearning.auth.audit.domain.EntityActionLogRepository
import com.keeplearning.auth.audit.domain.EntityType
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.entity.KcGroup
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.entity.KcRole
import com.keeplearning.auth.domain.repository.KcClientRepository
import com.keeplearning.auth.domain.repository.KcGroupRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.KcRoleRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.RoleRepresentation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class RevertServiceTest {

    @MockK
    lateinit var auditRepository: EntityActionLogRepository

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var roleRepository: KcRoleRepository

    @MockK
    lateinit var groupRepository: KcGroupRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var objectMapper: ObjectMapper

    private lateinit var service: RevertService

    private val actionId = UUID.randomUUID()
    private val entityId = UUID.randomUUID()
    private val realmId = UUID.randomUUID()
    private val revertLogId = UUID.randomUUID()
    private val actorKeycloakId = "actor-kc-id-123"

    private val realm = KcRealm(
        id = realmId,
        realmName = "test-realm",
        keycloakId = "test-realm-kc-id"
    )

    private val actor: JwtAuthenticationToken by lazy {
        val jwt = mockk<Jwt>()
        every { jwt.subject } returns actorKeycloakId
        every { jwt.claims } returns mapOf(
            "sub" to actorKeycloakId,
            "email" to "admin@example.com",
            "name" to "Admin User",
            "preferred_username" to "adminuser",
            "iss" to "https://auth.example.com/realms/test"
        )
        every { jwt.issuer } returns URL("https://auth.example.com/realms/test")

        val token = mockk<JwtAuthenticationToken>()
        every { token.token } returns jwt
        token
    }

    private fun buildAction(
        entityType: EntityType,
        actionType: ActionType,
        reverted: Boolean = false,
        entityKeycloakId: String? = "kc-entity-id",
        beforeState: Json? = null,
        afterState: Json? = null
    ) = EntityActionLog(
        id = actionId,
        actorKeycloakId = "original-actor",
        actorEmail = "original@example.com",
        actorDisplayName = "Original Actor",
        actionType = actionType,
        entityType = entityType,
        entityId = entityId,
        entityKeycloakId = entityKeycloakId,
        entityName = "test-entity",
        realmName = "test-realm",
        realmId = realmId,
        beforeState = beforeState,
        afterState = afterState,
        reverted = reverted,
        createdAt = Instant.parse("2024-06-01T10:00:00Z")
    )

    private fun stubMarkAsReverted() {
        every { auditRepository.save(match<EntityActionLog> { it.id == actionId && it.reverted }) } answers {
            Mono.just(firstArg())
        }
        every { auditRepository.save(match<EntityActionLog> { it.id == null }) } answers {
            Mono.just(firstArg<EntityActionLog>().copy(id = revertLogId))
        }
    }

    @BeforeEach
    fun setup() {
        service = RevertService(
            auditRepository,
            keycloakClient,
            clientRepository,
            roleRepository,
            groupRepository,
            realmRepository,
            objectMapper
        )
    }

    // --- Validation ---

    @Test
    fun `revertAction throws 404 when action not found`() = runTest {
        every { auditRepository.findById(actionId) } returns Mono.empty()

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo", actor)
        }
        assertEquals(404, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Action not found"))
    }

    @Test
    fun `revertAction throws 400 when action already reverted`() = runTest {
        val action = buildAction(EntityType.CLIENT, ActionType.CREATE, reverted = true)
        every { auditRepository.findById(actionId) } returns Mono.just(action)

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo", actor)
        }
        assertEquals(400, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("already been reverted"))
    }

    @Test
    fun `revertAction throws 409 when subsequent actions exist`() = runTest {
        val action = buildAction(EntityType.CLIENT, ActionType.CREATE)
        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every {
            auditRepository.canRevertAction("CLIENT", entityId, action.createdAt)
        } returns Mono.just(false)

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo", actor)
        }
        assertEquals(409, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("subsequent actions"))
    }

    @Test
    fun `revertAction throws 400 for REALM entity type`() = runTest {
        val action = buildAction(EntityType.REALM, ActionType.CREATE)
        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every {
            auditRepository.canRevertAction("REALM", entityId, action.createdAt)
        } returns Mono.just(true)

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo", actor)
        }
        assertEquals(400, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("Realm actions cannot be reverted"))
    }

    @Test
    fun `revertAction throws 400 for USER entity type`() = runTest {
        val action = buildAction(EntityType.USER, ActionType.CREATE)
        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every {
            auditRepository.canRevertAction("USER", entityId, action.createdAt)
        } returns Mono.just(true)

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo", actor)
        }
        assertEquals(400, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("User actions cannot be reverted"))
    }

    // --- revertClient CREATE ---

    @Test
    fun `revertAction CLIENT CREATE deletes client from Keycloak and DB and marks as reverted`() = runTest {
        val action = buildAction(EntityType.CLIENT, ActionType.CREATE, entityKeycloakId = "kc-client-uuid")
        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("CLIENT", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.deleteClient("test-realm", "kc-client-uuid") } returns Unit
        every { clientRepository.deleteById(entityId) } returns Mono.empty()
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "undo create", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Client deleted"))
        assertTrue(result.message.contains("reverted CREATE"))

        verify { clientRepository.deleteById(entityId) }
    }

    // --- revertClient DELETE ---

    @Test
    fun `revertAction CLIENT DELETE restores client from beforeState`() = runTest {
        val beforeJson = """{"clientId":"my-app","name":"My App","description":"desc","enabled":true,"publicClient":true,"standardFlowEnabled":true,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,"rootUrl":"http://localhost","baseUrl":"/","redirectUris":["http://localhost/*"],"webOrigins":["http://localhost"]}"""
        val action = buildAction(
            EntityType.CLIENT, ActionType.DELETE,
            beforeState = Json.of(beforeJson),
            entityKeycloakId = "old-kc-id"
        )

        val beforeMap = mapOf<String, Any?>(
            "clientId" to "my-app",
            "name" to "My App",
            "description" to "desc",
            "enabled" to true,
            "publicClient" to true,
            "standardFlowEnabled" to true,
            "directAccessGrantsEnabled" to false,
            "serviceAccountsEnabled" to false,
            "rootUrl" to "http://localhost",
            "baseUrl" to "/",
            "redirectUris" to listOf("http://localhost/*"),
            "webOrigins" to listOf("http://localhost")
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("CLIENT", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.createClient("test-realm", any()) } returns "new-kc-client-id"
        every { objectMapper.writeValueAsString(listOf("http://localhost/*")) } returns """["http://localhost/*"]"""
        every { objectMapper.writeValueAsString(listOf("http://localhost")) } returns """["http://localhost"]"""
        every { clientRepository.save(any()) } answers {
            Mono.just(firstArg<KcClient>().copy(id = entityId))
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "restore deleted client", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Client restored"))
        assertTrue(result.message.contains("reverted DELETE"))
        assertEquals(revertLogId, result.newActionId)

        val savedClient = slot<KcClient>()
        verify { clientRepository.save(capture(savedClient)) }
        assertEquals("my-app", savedClient.captured.clientId)
        assertEquals("My App", savedClient.captured.name)
        assertEquals("new-kc-client-id", savedClient.captured.keycloakId)
    }

    // --- revertClient UPDATE ---

    @Test
    fun `revertAction CLIENT UPDATE restores client to before state`() = runTest {
        val beforeJson = """{"clientId":"my-app","name":"Old Name","description":"old desc","enabled":true,"publicClient":true,"standardFlowEnabled":true,"directAccessGrantsEnabled":false,"serviceAccountsEnabled":false}"""
        val action = buildAction(
            EntityType.CLIENT, ActionType.UPDATE,
            beforeState = Json.of(beforeJson),
            entityKeycloakId = "kc-client-uuid"
        )

        val beforeMap = mapOf<String, Any?>(
            "clientId" to "my-app",
            "name" to "Old Name",
            "description" to "old desc",
            "enabled" to true,
            "publicClient" to true,
            "standardFlowEnabled" to true,
            "directAccessGrantsEnabled" to false,
            "serviceAccountsEnabled" to false
        )

        val existingClient = KcClient(
            id = entityId,
            realmId = realmId,
            clientId = "my-app",
            name = "New Name",
            keycloakId = "kc-client-uuid"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("CLIENT", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.updateClient("test-realm", "kc-client-uuid", any()) } returns Unit
        every { clientRepository.findById(entityId) } returns Mono.just(existingClient)
        every { objectMapper.writeValueAsString(emptyList<String>()) } returns "[]"
        every { clientRepository.save(any()) } answers {
            Mono.just(firstArg<KcClient>())
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "restore update", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("restored to previous state"))
        assertTrue(result.message.contains("reverted UPDATE"))
        assertEquals(revertLogId, result.newActionId)

        val savedClient = slot<KcClient>()
        verify { clientRepository.save(capture(savedClient)) }
        assertEquals("Old Name", savedClient.captured.name)
        assertEquals("old desc", savedClient.captured.description)
    }

    // --- revertRole CREATE ---

    @Test
    fun `revertAction ROLE CREATE deletes the created realm role`() = runTest {
        val afterJson = """{"name":"test-role","description":"A test role"}"""
        val action = buildAction(
            EntityType.ROLE, ActionType.CREATE,
            afterState = Json.of(afterJson)
        )

        val afterMap = mapOf<String, Any?>(
            "name" to "test-role",
            "description" to "A test role"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("ROLE", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(afterJson, Map::class.java) } returns afterMap
        coEvery { keycloakClient.deleteRealmRole("test-realm", "test-role") } returns Unit
        every { roleRepository.deleteById(entityId) } returns Mono.empty()
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "undo role create", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Role deleted"))
        assertTrue(result.message.contains("reverted CREATE"))

        verify { roleRepository.deleteById(entityId) }
    }

    // --- revertRole DELETE ---

    @Test
    fun `revertAction ROLE DELETE restores realm role from beforeState`() = runTest {
        val beforeJson = """{"name":"restored-role","description":"Restored"}"""
        val action = buildAction(
            EntityType.ROLE, ActionType.DELETE,
            beforeState = Json.of(beforeJson)
        )

        val beforeMap = mapOf<String, Any?>(
            "name" to "restored-role",
            "description" to "Restored"
        )

        val createdRole = RoleRepresentation(
            id = "kc-role-id-new",
            name = "restored-role",
            description = "Restored"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("ROLE", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.createRealmRole("test-realm", any()) } returns Unit
        coEvery { keycloakClient.getRealmRole("test-realm", "restored-role") } returns createdRole
        every { roleRepository.save(any()) } answers {
            Mono.just(firstArg<KcRole>().copy(id = entityId))
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "restore deleted role", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Role restored"))
        assertTrue(result.message.contains("reverted DELETE"))
        assertEquals(revertLogId, result.newActionId)

        val savedRole = slot<KcRole>()
        verify { roleRepository.save(capture(savedRole)) }
        assertEquals("restored-role", savedRole.captured.name)
        assertEquals("Restored", savedRole.captured.description)
        assertEquals("kc-role-id-new", savedRole.captured.keycloakId)
    }

    // --- revertRole UPDATE ---

    @Test
    fun `revertAction ROLE UPDATE restores realm role to before state`() = runTest {
        val beforeJson = """{"name":"my-role","description":"Old description"}"""
        val action = buildAction(
            EntityType.ROLE, ActionType.UPDATE,
            beforeState = Json.of(beforeJson)
        )

        val beforeMap = mapOf<String, Any?>(
            "name" to "my-role",
            "description" to "Old description"
        )

        val existingRole = KcRole(
            id = entityId,
            realmId = realmId,
            name = "my-role",
            description = "New description",
            keycloakId = "kc-role-id"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("ROLE", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.updateRealmRole("test-realm", "my-role", any()) } returns Unit
        every { roleRepository.findById(entityId) } returns Mono.just(existingRole)
        every { roleRepository.save(any()) } answers {
            Mono.just(firstArg<KcRole>())
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "revert role update", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("restored to previous state"))
        assertTrue(result.message.contains("reverted UPDATE"))
        assertEquals(revertLogId, result.newActionId)

        val savedRole = slot<KcRole>()
        verify { roleRepository.save(capture(savedRole)) }
        assertEquals("Old description", savedRole.captured.description)
    }

    // --- revertGroup CREATE ---

    @Test
    fun `revertAction GROUP CREATE deletes the created group`() = runTest {
        val action = buildAction(
            EntityType.GROUP, ActionType.CREATE,
            entityKeycloakId = "kc-group-uuid"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("GROUP", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.deleteGroup("test-realm", "kc-group-uuid") } returns Unit
        every { groupRepository.deleteById(entityId) } returns Mono.empty()
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "undo group create", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Group deleted"))
        assertTrue(result.message.contains("reverted CREATE"))

        verify { groupRepository.deleteById(entityId) }
    }

    // --- revertGroup DELETE ---

    @Test
    fun `revertAction GROUP DELETE restores group from beforeState`() = runTest {
        val beforeJson = """{"name":"test-group","path":"/test-group"}"""
        val action = buildAction(
            EntityType.GROUP, ActionType.DELETE,
            beforeState = Json.of(beforeJson)
        )

        val beforeMap = mapOf<String, Any?>(
            "name" to "test-group",
            "path" to "/test-group"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("GROUP", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.createGroup("test-realm", any()) } returns "new-kc-group-id"
        every { groupRepository.save(any()) } answers {
            Mono.just(firstArg<KcGroup>().copy(id = entityId))
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "restore deleted group", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("Group restored"))
        assertTrue(result.message.contains("reverted DELETE"))
        assertEquals(revertLogId, result.newActionId)

        val savedGroup = slot<KcGroup>()
        verify { groupRepository.save(capture(savedGroup)) }
        assertEquals("test-group", savedGroup.captured.name)
        assertEquals("/test-group", savedGroup.captured.path)
        assertEquals("new-kc-group-id", savedGroup.captured.keycloakId)
    }

    // --- revertGroup UPDATE ---

    @Test
    fun `revertAction GROUP UPDATE restores group to before state`() = runTest {
        val beforeJson = """{"name":"old-group-name","path":"/old-group-name"}"""
        val action = buildAction(
            EntityType.GROUP, ActionType.UPDATE,
            beforeState = Json.of(beforeJson),
            entityKeycloakId = "kc-group-uuid"
        )

        val beforeMap = mapOf<String, Any?>(
            "name" to "old-group-name",
            "path" to "/old-group-name"
        )

        val existingGroup = KcGroup(
            id = entityId,
            realmId = realmId,
            name = "new-group-name",
            path = "/new-group-name",
            keycloakId = "kc-group-uuid"
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("GROUP", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        every { objectMapper.readValue(beforeJson, Map::class.java) } returns beforeMap
        coEvery { keycloakClient.updateGroup("test-realm", "kc-group-uuid", any()) } returns Unit
        every { groupRepository.findById(entityId) } returns Mono.just(existingGroup)
        every { groupRepository.save(any()) } answers {
            Mono.just(firstArg<KcGroup>())
        }
        stubMarkAsReverted()

        val result = service.revertAction(actionId, "revert group update", actor)

        assertTrue(result.success)
        assertTrue(result.message.contains("restored to previous state"))
        assertTrue(result.message.contains("reverted UPDATE"))
        assertEquals(revertLogId, result.newActionId)

        val savedGroup = slot<KcGroup>()
        verify { groupRepository.save(capture(savedGroup)) }
        assertEquals("old-group-name", savedGroup.captured.name)
        assertEquals("/old-group-name", savedGroup.captured.path)
    }

    // --- revertIdp ---

    @Test
    fun `revertAction IDP throws NOT_IMPLEMENTED`() = runTest {
        val action = buildAction(EntityType.IDP, ActionType.CREATE)
        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("IDP", entityId, action.createdAt) } returns Mono.just(true)

        val ex = assertThrows<ResponseStatusException> {
            service.revertAction(actionId, "undo idp", actor)
        }
        assertEquals(501, ex.statusCode.value())
        assertTrue(ex.reason!!.contains("IDP revert is not yet implemented"))
    }

    // --- markAsReverted verification ---

    @Test
    fun `markAsReverted creates revert log with inverse action type and swapped states`() = runTest {
        val beforeJson = """{"clientId":"my-app","name":"Before"}"""
        val afterJson = """{"clientId":"my-app","name":"After"}"""
        val action = buildAction(
            EntityType.CLIENT, ActionType.CREATE,
            entityKeycloakId = "kc-client-uuid",
            beforeState = Json.of(beforeJson),
            afterState = Json.of(afterJson)
        )

        every { auditRepository.findById(actionId) } returns Mono.just(action)
        every { auditRepository.canRevertAction("CLIENT", entityId, action.createdAt) } returns Mono.just(true)
        every { realmRepository.findByRealmName("test-realm") } returns Mono.just(realm)
        coEvery { keycloakClient.deleteClient("test-realm", "kc-client-uuid") } returns Unit
        every { clientRepository.deleteById(entityId) } returns Mono.empty()

        // Capture both saves: the reverted original and the new revert log
        val savedLogs = mutableListOf<EntityActionLog>()
        every { auditRepository.save(capture(savedLogs)) } answers {
            val arg = firstArg<EntityActionLog>()
            if (arg.id == null) {
                Mono.just(arg.copy(id = revertLogId))
            } else {
                Mono.just(arg)
            }
        }

        service.revertAction(actionId, "revert reason", actor)

        assertEquals(2, savedLogs.size)

        // First save: original action marked as reverted
        val revertedOriginal = savedLogs[0]
        assertTrue(revertedOriginal.reverted)
        assertEquals(actorKeycloakId, revertedOriginal.revertedByKeycloakId)
        assertEquals("revert reason", revertedOriginal.revertReason)

        // Second save: the new revert audit log
        val revertLog = savedLogs[1]
        assertEquals(ActionType.DELETE, revertLog.actionType) // inverse of CREATE
        assertEquals(EntityType.CLIENT, revertLog.entityType)
        assertEquals(entityId, revertLog.entityId)
        assertEquals(actorKeycloakId, revertLog.actorKeycloakId)
        assertEquals("admin@example.com", revertLog.actorEmail)
        assertEquals("Admin User", revertLog.actorDisplayName)
        assertEquals("https://auth.example.com/realms/test", revertLog.actorIssuer)
        assertEquals(actionId, revertLog.revertOfActionId)
        // States are swapped: before becomes after's original, after becomes before's original
        assertEquals(action.afterState, revertLog.beforeState)
        assertEquals(action.beforeState, revertLog.afterState)
    }
}
