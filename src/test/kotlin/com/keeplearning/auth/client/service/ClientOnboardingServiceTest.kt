package com.keeplearning.auth.client.service

import com.keeplearning.auth.client.dto.OnboardStatus
import com.keeplearning.auth.client.dto.OnboardUserRequest
import com.keeplearning.auth.client.dto.OnboardUsersRequest
import com.keeplearning.auth.domain.entity.*
import com.keeplearning.auth.domain.repository.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ClientOnboardingServiceTest {

    @MockK
    lateinit var clientRepository: KcClientRepository

    @MockK
    lateinit var realmRepository: KcRealmRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var userClientRepository: KcUserClientRepository

    @MockK
    lateinit var roleRepository: ClientCustomRoleRepository

    @MockK
    lateinit var userRoleRepository: UserClientCustomRoleRepository

    @MockK
    lateinit var jwt: Jwt

    private lateinit var service: ClientOnboardingService

    private val realmId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val backendClientId = UUID.randomUUID()
    private val frontendClientId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val roleId = UUID.randomUUID()

    private val realm = KcRealm(
        id = realmId,
        accountId = accountId,
        realmName = "test-realm",
        keycloakId = "test-realm-kc-id"
    )

    private val backendClient = KcClient(
        id = backendClientId,
        realmId = realmId,
        clientId = "ecs-backend",
        keycloakId = "ecs-backend",
        serviceAccountsEnabled = true,
        publicClient = false
    )

    private val frontendClient = KcClient(
        id = frontendClientId,
        realmId = realmId,
        clientId = "ecs-web",
        keycloakId = "ecs-web",
        pairedClientId = backendClientId,
        publicClient = true
    )

    private val defaultRole = ClientCustomRole(
        id = roleId,
        clientId = frontendClientId,
        name = "user",
        displayName = "User",
        isDefault = true
    )

    @BeforeEach
    fun setup() {
        service = ClientOnboardingService(
            clientRepository,
            realmRepository,
            userRepository,
            userClientRepository,
            roleRepository,
            userRoleRepository
        )
    }

    @Test
    fun `onboardUsers creates new user with default role`() = runTest {
        // Setup JWT
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { jwt.subject } returns "service-account-ecs-backend"

        // Backend client is found by azp
        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)

        // Frontend client is found as paired
        every { clientRepository.findByPairedClientId(backendClientId) } returns Mono.just(frontendClient)

        // Realm lookup
        every { realmRepository.findById(realmId) } returns Mono.just(realm)

        // Roles for frontend client
        every { roleRepository.findByClientId(frontendClientId) } returns Flux.just(defaultRole)

        // User doesn't exist yet
        every { userRepository.findByRealmIdAndEmail(realmId, "john@example.com") } returns Mono.empty()

        // User creation
        val newUser = User(
            id = userId,
            keycloakUserId = "",
            email = "john@example.com",
            firstName = "John",
            lastName = "Doe",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )
        every { userRepository.save(any()) } returns Mono.just(newUser)

        // User-client authorization doesn't exist
        every { userClientRepository.existsByClientIdAndUserEmail(frontendClientId, "john@example.com") } returns Mono.just(false)
        every { userClientRepository.save(any()) } returns Mono.just(mockk<KcUserClient>())

        // Role assignment
        every { userRoleRepository.findByUserIdAndCustomRoleId(userId, roleId) } returns Mono.empty()
        every { userRoleRepository.save(any()) } returns Mono.just(mockk<UserClientCustomRole>())

        // Execute
        val request = OnboardUsersRequest(
            users = listOf(
                OnboardUserRequest(
                    email = "john@example.com",
                    firstName = "John",
                    lastName = "Doe"
                )
            )
        )

        val response = service.onboardUsers(jwt, request)

        // Verify
        assertEquals(1, response.summary.total)
        assertEquals(1, response.summary.created)
        assertEquals(0, response.summary.alreadyExists)
        assertEquals(0, response.summary.failed)
        assertEquals(OnboardStatus.CREATED, response.results[0].status)
        assertEquals(listOf("user"), response.results[0].assignedRoles)

        verify { userRepository.save(any()) }
        verify { userClientRepository.save(any()) }
        verify { userRoleRepository.save(any()) }
    }

    @Test
    fun `onboardUsers assigns specified roles`() = runTest {
        val adminRole = ClientCustomRole(
            id = UUID.randomUUID(),
            clientId = frontendClientId,
            name = "admin",
            displayName = "Admin",
            isDefault = false
        )

        // Setup JWT
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { jwt.subject } returns "service-account-ecs-backend"

        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)
        every { clientRepository.findByPairedClientId(backendClientId) } returns Mono.just(frontendClient)
        every { realmRepository.findById(realmId) } returns Mono.just(realm)
        every { roleRepository.findByClientId(frontendClientId) } returns Flux.just(defaultRole, adminRole)
        every { userRepository.findByRealmIdAndEmail(realmId, "admin@example.com") } returns Mono.empty()

        val newUser = User(
            id = userId,
            keycloakUserId = "",
            email = "admin@example.com",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )
        every { userRepository.save(any()) } returns Mono.just(newUser)
        every { userClientRepository.existsByClientIdAndUserEmail(frontendClientId, "admin@example.com") } returns Mono.just(false)
        every { userClientRepository.save(any()) } returns Mono.just(mockk<KcUserClient>())
        every { userRoleRepository.findByUserIdAndCustomRoleId(userId, any()) } returns Mono.empty()
        every { userRoleRepository.save(any()) } returns Mono.just(mockk<UserClientCustomRole>())

        val request = OnboardUsersRequest(
            users = listOf(
                OnboardUserRequest(
                    email = "admin@example.com",
                    roles = listOf("admin")
                )
            )
        )

        val response = service.onboardUsers(jwt, request)

        assertEquals(1, response.summary.created)
        assertEquals(listOf("admin"), response.results[0].assignedRoles)
    }

    @Test
    fun `onboardUsers returns already exists for existing user`() = runTest {
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { jwt.subject } returns "service-account-ecs-backend"

        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)
        every { clientRepository.findByPairedClientId(backendClientId) } returns Mono.just(frontendClient)
        every { realmRepository.findById(realmId) } returns Mono.just(realm)
        every { roleRepository.findByClientId(frontendClientId) } returns Flux.just(defaultRole)

        val existingUser = User(
            id = userId,
            keycloakUserId = "kc-user-id",
            email = "existing@example.com",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )
        every { userRepository.findByRealmIdAndEmail(realmId, "existing@example.com") } returns Mono.just(existingUser)
        every { userClientRepository.existsByClientIdAndUserEmail(frontendClientId, "existing@example.com") } returns Mono.just(true)

        val request = OnboardUsersRequest(
            users = listOf(
                OnboardUserRequest(email = "existing@example.com")
            )
        )

        val response = service.onboardUsers(jwt, request)

        assertEquals(0, response.summary.created)
        assertEquals(1, response.summary.alreadyExists)
        assertEquals(OnboardStatus.ALREADY_EXISTS, response.results[0].status)
    }

    @Test
    fun `onboardUsers fails when no roles defined`() = runTest {
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { jwt.subject } returns "service-account-ecs-backend"

        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)
        every { clientRepository.findByPairedClientId(backendClientId) } returns Mono.just(frontendClient)
        every { realmRepository.findById(realmId) } returns Mono.just(realm)
        every { roleRepository.findByClientId(frontendClientId) } returns Flux.empty()

        val request = OnboardUsersRequest(
            users = listOf(
                OnboardUserRequest(email = "john@example.com")
            )
        )

        val exception = assertThrows<ResponseStatusException> {
            service.onboardUsers(jwt, request)
        }

        assert(exception.message!!.contains("No roles defined"))
    }

    @Test
    fun `onboardUsers fails for invalid roles`() = runTest {
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { jwt.subject } returns "service-account-ecs-backend"

        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)
        every { clientRepository.findByPairedClientId(backendClientId) } returns Mono.just(frontendClient)
        every { realmRepository.findById(realmId) } returns Mono.just(realm)
        every { roleRepository.findByClientId(frontendClientId) } returns Flux.just(defaultRole)
        every { userRepository.findByRealmIdAndEmail(realmId, "john@example.com") } returns Mono.empty()

        val newUser = User(
            id = userId,
            keycloakUserId = "",
            email = "john@example.com",
            accountId = accountId,
            realmId = realmId,
            status = "ACTIVE"
        )
        every { userRepository.save(any()) } returns Mono.just(newUser)
        every { userClientRepository.existsByClientIdAndUserEmail(frontendClientId, "john@example.com") } returns Mono.just(false)
        every { userClientRepository.save(any()) } returns Mono.just(mockk<KcUserClient>())

        val request = OnboardUsersRequest(
            users = listOf(
                OnboardUserRequest(
                    email = "john@example.com",
                    roles = listOf("nonexistent-role")
                )
            )
        )

        val response = service.onboardUsers(jwt, request)

        assertEquals(1, response.summary.failed)
        assert(response.results[0].error!!.contains("Invalid roles"))
    }

    @Test
    fun `verifyOnboardAccess returns true for service account client`() = runTest {
        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(backendClient)

        val result = service.verifyOnboardAccess(jwt)

        assertEquals(true, result)
    }

    @Test
    fun `verifyOnboardAccess returns false for public client`() = runTest {
        val publicClient = backendClient.copy(serviceAccountsEnabled = false)

        every { jwt.getClaimAsString("azp") } returns "ecs-backend"
        every { clientRepository.findByKeycloakId("ecs-backend") } returns Mono.just(publicClient)

        val result = service.verifyOnboardAccess(jwt)

        assertEquals(false, result)
    }
}
