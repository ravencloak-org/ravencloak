package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.CreateClientRoleRequest
import com.keeplearning.auth.client.dto.UpdateClientRoleRequest
import com.keeplearning.auth.domain.entity.ClientCustomRole
import com.keeplearning.auth.domain.entity.KcClient
import com.keeplearning.auth.domain.repository.ClientCustomRoleRepository
import com.keeplearning.auth.domain.repository.KcClientRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ClientRoleServiceTest {

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var roleRepository: ClientCustomRoleRepository

    private lateinit var service: ClientRoleService

    private val clientId = UUID.randomUUID()
    private val roleId = UUID.randomUUID()
    private val realmId = UUID.randomUUID()

    private val client = KcClient(
        id = clientId,
        realmId = realmId,
        clientId = "test-client",
        keycloakId = "test-client-kc-id"
    )

    private val role = ClientCustomRole(
        id = roleId,
        clientId = clientId,
        name = "admin",
        displayName = "Administrator",
        description = "Admin role",
        isDefault = false
    )

    @BeforeEach
    fun setup() {
        service = ClientRoleService(clientRepository, roleRepository)
    }

    @Test
    fun `createRole creates new role successfully`() = runTest {
        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.existsByClientIdAndName(clientId, "editor") } returns Mono.just(false)
        every { roleRepository.save(any()) } answers {
            val saved = firstArg<ClientCustomRole>()
            Mono.just(saved.copy(id = UUID.randomUUID()))
        }

        val request = CreateClientRoleRequest(
            name = "editor",
            displayName = "Editor",
            description = "Can edit content",
            isDefault = false
        )

        val response = service.createRole(clientId, request)

        assertEquals("editor", response.name)
        assertEquals("Editor", response.displayName)
        assertFalse(response.isDefault)
    }

    @Test
    fun `createRole with isDefault clears existing default`() = runTest {
        val existingDefault = role.copy(isDefault = true)

        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.existsByClientIdAndName(clientId, "new-default") } returns Mono.just(false)
        every { roleRepository.findByClientIdAndIsDefaultTrue(clientId) } returns Mono.just(existingDefault)
        every { roleRepository.save(any()) } answers {
            Mono.just(firstArg<ClientCustomRole>().copy(id = UUID.randomUUID()))
        }

        val request = CreateClientRoleRequest(
            name = "new-default",
            isDefault = true
        )

        val response = service.createRole(clientId, request)

        assertTrue(response.isDefault)
        // Verify the existing default was cleared
        verify { roleRepository.save(match { !it.isDefault && it.name == "admin" }) }
    }

    @Test
    fun `createRole fails for duplicate name`() = runTest {
        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.existsByClientIdAndName(clientId, "admin") } returns Mono.just(true)

        val request = CreateClientRoleRequest(name = "admin")

        val exception = assertThrows<ResponseStatusException> {
            service.createRole(clientId, request)
        }

        assert(exception.message!!.contains("already exists"))
    }

    @Test
    fun `listRoles returns all roles for client`() = runTest {
        val roles = listOf(
            role,
            role.copy(id = UUID.randomUUID(), name = "editor")
        )

        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.findByClientId(clientId) } returns Flux.fromIterable(roles)

        val response = service.listRoles(clientId)

        assertEquals(2, response.size)
    }

    @Test
    fun `updateRole updates description and default flag`() = runTest {
        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.findByClientIdAndName(clientId, "admin") } returns Mono.just(role)
        every { roleRepository.findByClientIdAndIsDefaultTrue(clientId) } returns Mono.empty()
        every { roleRepository.save(any()) } answers {
            Mono.just(firstArg<ClientCustomRole>())
        }

        val request = UpdateClientRoleRequest(
            description = "Updated description",
            isDefault = true
        )

        val response = service.updateRole(clientId, "admin", request)

        assertEquals("Updated description", response.description)
        assertTrue(response.isDefault)
    }

    @Test
    fun `deleteRole removes role`() = runTest {
        every { clientRepository.findById(clientId) } returns Mono.just(client)
        every { roleRepository.findByClientIdAndName(clientId, "admin") } returns Mono.just(role)
        every { roleRepository.delete(role) } returns Mono.empty()

        service.deleteRole(clientId, "admin")

        verify { roleRepository.delete(role) }
    }

    @Test
    fun `getDefaultRole returns default role when exists`() = runTest {
        val defaultRole = role.copy(isDefault = true)
        every { roleRepository.findByClientIdAndIsDefaultTrue(clientId) } returns Mono.just(defaultRole)

        val result = service.getDefaultRole(clientId)

        assertEquals(defaultRole, result)
    }

    @Test
    fun `getDefaultRole returns null when no default`() = runTest {
        every { roleRepository.findByClientIdAndIsDefaultTrue(clientId) } returns Mono.empty()

        val result = service.getDefaultRole(clientId)

        assertEquals(null, result)
    }
}
