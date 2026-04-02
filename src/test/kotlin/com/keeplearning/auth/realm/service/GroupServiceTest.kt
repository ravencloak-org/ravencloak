package com.keeplearning.auth.realm.service

import com.keeplearning.auth.audit.service.AuditService
import com.keeplearning.auth.domain.entity.KcGroup
import com.keeplearning.auth.domain.entity.KcRealm
import com.keeplearning.auth.domain.repository.KcGroupRepository
import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.keycloak.client.KeycloakAdminClient
import com.keeplearning.auth.keycloak.client.dto.GroupRepresentation
import com.keeplearning.auth.realm.dto.CreateGroupRequest
import com.keeplearning.auth.realm.dto.UpdateGroupRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class GroupServiceTest {

    @MockK
    lateinit var keycloakClient: KeycloakAdminClient

    @MockK
    lateinit var groupRepository: KcGroupRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var auditService: AuditService

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private lateinit var service: GroupService

    private val realmId = UUID.randomUUID()
    private val realmName = "test-realm"
    private val groupId = UUID.randomUUID()
    private val parentGroupId = UUID.randomUUID()
    private val keycloakGroupId = "kc-group-id"
    private val keycloakParentGroupId = "kc-parent-group-id"

    private val realm = KcRealm(
        id = realmId,
        realmName = realmName,
        keycloakId = "kc-realm-id"
    )

    private val group = KcGroup(
        id = groupId,
        realmId = realmId,
        parentId = null,
        name = "test-group",
        path = "/test-group",
        attributes = null,
        keycloakId = keycloakGroupId
    )

    private val parentGroup = KcGroup(
        id = parentGroupId,
        realmId = realmId,
        parentId = null,
        name = "parent-group",
        path = "/parent-group",
        attributes = null,
        keycloakId = keycloakParentGroupId
    )

    @BeforeEach
    fun setup() {
        service = GroupService(keycloakClient, groupRepository, realmRepository, auditService, objectMapper)
    }

    // ==================== createGroup ====================

    @Test
    fun `createGroup creates group successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createGroup(realmName, any()) } returns keycloakGroupId
        every { groupRepository.save(any()) } answers {
            val saved = firstArg<KcGroup>()
            Mono.just(saved.copy(id = groupId))
        }

        val request = CreateGroupRequest(name = "test-group")
        val response = service.createGroup(realmName, request, actor = null)

        assertEquals(groupId, response.id)
        assertEquals("test-group", response.name)
        assertEquals("/test-group", response.path)

        coVerify { keycloakClient.createGroup(realmName, match { it.name == "test-group" }) }
    }

    @Test
    fun `createGroup with attributes creates group successfully`() = runTest {
        val attributes = mapOf("department" to listOf("Engineering"))

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        coEvery { keycloakClient.createGroup(realmName, any()) } returns keycloakGroupId
        every { groupRepository.save(any()) } answers {
            val saved = firstArg<KcGroup>()
            Mono.just(saved.copy(id = groupId))
        }

        val request = CreateGroupRequest(name = "test-group", attributes = attributes)
        val response = service.createGroup(realmName, request, actor = null)

        assertEquals("test-group", response.name)

        coVerify {
            keycloakClient.createGroup(realmName, match {
                it.name == "test-group" && it.attributes == attributes
            })
        }
    }

    @Test
    fun `createGroup throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val request = CreateGroupRequest(name = "test-group")

        val exception = assertThrows<ResponseStatusException> {
            service.createGroup("unknown-realm", request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== createSubgroup ====================

    @Test
    fun `createSubgroup creates subgroup successfully`() = runTest {
        val subKeycloakId = "kc-sub-group-id"
        val subGroupId = UUID.randomUUID()

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId(keycloakParentGroupId) } returns Mono.just(parentGroup)
        coEvery { keycloakClient.createSubgroup(realmName, keycloakParentGroupId, any()) } returns subKeycloakId
        every { groupRepository.save(any()) } answers {
            val saved = firstArg<KcGroup>()
            Mono.just(saved.copy(id = subGroupId))
        }

        val request = CreateGroupRequest(name = "child-group")
        val response = service.createSubgroup(realmName, keycloakParentGroupId, request, actor = null)

        assertEquals(subGroupId, response.id)
        assertEquals("child-group", response.name)
        assertEquals("/parent-group/child-group", response.path)

        coVerify { keycloakClient.createSubgroup(realmName, keycloakParentGroupId, match { it.name == "child-group" }) }
    }

    @Test
    fun `createSubgroup throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val request = CreateGroupRequest(name = "child-group")

        val exception = assertThrows<ResponseStatusException> {
            service.createSubgroup("unknown-realm", keycloakParentGroupId, request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    @Test
    fun `createSubgroup throws 404 when parent group not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId("nonexistent-parent") } returns Mono.empty()

        val request = CreateGroupRequest(name = "child-group")

        val exception = assertThrows<ResponseStatusException> {
            service.createSubgroup(realmName, "nonexistent-parent", request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Parent group"))
    }

    // ==================== getGroup ====================

    @Test
    fun `getGroup returns group successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId(keycloakGroupId) } returns Mono.just(group)

        val response = service.getGroup(realmName, keycloakGroupId)

        assertEquals(groupId, response.id)
        assertEquals("test-group", response.name)
        assertEquals("/test-group", response.path)
    }

    @Test
    fun `getGroup throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getGroup("unknown-realm", keycloakGroupId)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    @Test
    fun `getGroup throws 404 when group not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId("nonexistent") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getGroup(realmName, "nonexistent")
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Group"))
    }

    // ==================== listGroups ====================

    @Test
    fun `listGroups returns root groups successfully`() = runTest {
        val group2 = group.copy(
            id = UUID.randomUUID(),
            name = "second-group",
            path = "/second-group",
            keycloakId = "kc-group-id-2"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByRealmIdAndParentIdIsNull(realmId) } returns Flux.just(group, group2)

        val response = service.listGroups(realmName)

        assertEquals(2, response.size)
        assertEquals("test-group", response[0].name)
        assertEquals("second-group", response[1].name)
    }

    @Test
    fun `listGroups returns empty list when no groups`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByRealmIdAndParentIdIsNull(realmId) } returns Flux.empty()

        val response = service.listGroups(realmName)

        assertEquals(0, response.size)
    }

    @Test
    fun `listGroups throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.listGroups("unknown-realm")
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    // ==================== updateGroup ====================

    @Test
    fun `updateGroup updates name successfully`() = runTest {
        val kcGroupRep = GroupRepresentation(
            id = keycloakGroupId,
            name = "test-group",
            path = "/test-group"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId(keycloakGroupId) } returns Mono.just(group)
        coEvery { keycloakClient.getGroup(realmName, keycloakGroupId) } returns kcGroupRep
        coEvery { keycloakClient.updateGroup(realmName, keycloakGroupId, any()) } returns Unit
        every { groupRepository.save(any()) } answers {
            Mono.just(firstArg<KcGroup>())
        }

        val request = UpdateGroupRequest(name = "renamed-group")
        val response = service.updateGroup(realmName, keycloakGroupId, request, actor = null)

        assertEquals("renamed-group", response.name)
        assertEquals("/renamed-group", response.path)

        coVerify { keycloakClient.updateGroup(realmName, keycloakGroupId, match { it.name == "renamed-group" }) }
    }

    @Test
    fun `updateGroup updates attributes successfully`() = runTest {
        val newAttributes = mapOf("location" to listOf("NYC"))
        val kcGroupRep = GroupRepresentation(
            id = keycloakGroupId,
            name = "test-group",
            path = "/test-group"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId(keycloakGroupId) } returns Mono.just(group)
        coEvery { keycloakClient.getGroup(realmName, keycloakGroupId) } returns kcGroupRep
        coEvery { keycloakClient.updateGroup(realmName, keycloakGroupId, any()) } returns Unit
        every { groupRepository.save(any()) } answers {
            Mono.just(firstArg<KcGroup>())
        }

        val request = UpdateGroupRequest(attributes = newAttributes)
        val response = service.updateGroup(realmName, keycloakGroupId, request, actor = null)

        assertEquals("test-group", response.name)
        assertEquals("/test-group", response.path)

        coVerify {
            keycloakClient.updateGroup(realmName, keycloakGroupId, match {
                it.attributes == newAttributes
            })
        }
    }

    @Test
    fun `updateGroup preserves path for child group when name changes`() = runTest {
        val childGroup = group.copy(
            id = UUID.randomUUID(),
            parentId = parentGroupId,
            name = "child",
            path = "/parent-group/child",
            keycloakId = "kc-child-id"
        )
        val kcGroupRep = GroupRepresentation(
            id = "kc-child-id",
            name = "child",
            path = "/parent-group/child"
        )

        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId("kc-child-id") } returns Mono.just(childGroup)
        coEvery { keycloakClient.getGroup(realmName, "kc-child-id") } returns kcGroupRep
        coEvery { keycloakClient.updateGroup(realmName, "kc-child-id", any()) } returns Unit
        every { groupRepository.save(any()) } answers {
            Mono.just(firstArg<KcGroup>())
        }

        val request = UpdateGroupRequest(name = "renamed-child")
        val response = service.updateGroup(realmName, "kc-child-id", request, actor = null)

        assertEquals("renamed-child", response.name)
        assertEquals("/parent-group/renamed-child", response.path)
    }

    @Test
    fun `updateGroup throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val request = UpdateGroupRequest(name = "new-name")

        val exception = assertThrows<ResponseStatusException> {
            service.updateGroup("unknown-realm", keycloakGroupId, request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    @Test
    fun `updateGroup throws 404 when group not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId("nonexistent") } returns Mono.empty()

        val request = UpdateGroupRequest(name = "new-name")

        val exception = assertThrows<ResponseStatusException> {
            service.updateGroup(realmName, "nonexistent", request, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Group"))
    }

    // ==================== deleteGroup ====================

    @Test
    fun `deleteGroup deletes group successfully`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId(keycloakGroupId) } returns Mono.just(group)
        coEvery { keycloakClient.deleteGroup(realmName, keycloakGroupId) } returns Unit
        every { groupRepository.delete(group) } returns Mono.empty()

        service.deleteGroup(realmName, keycloakGroupId, actor = null)

        coVerify { keycloakClient.deleteGroup(realmName, keycloakGroupId) }
    }

    @Test
    fun `deleteGroup throws 404 when realm not found`() = runTest {
        every { realmRepository.findByRealmName("unknown-realm") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.deleteGroup("unknown-realm", keycloakGroupId, actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Realm"))
    }

    @Test
    fun `deleteGroup throws 404 when group not found`() = runTest {
        every { realmRepository.findByRealmName(realmName) } returns Mono.just(realm)
        every { groupRepository.findByKeycloakId("nonexistent") } returns Mono.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.deleteGroup(realmName, "nonexistent", actor = null)
        }

        assertEquals(404, exception.statusCode.value())
        assert(exception.reason!!.contains("Group"))
    }
}
