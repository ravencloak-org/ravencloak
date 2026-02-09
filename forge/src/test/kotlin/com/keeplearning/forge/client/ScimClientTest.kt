package com.keeplearning.forge.client

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.config.ForgeProperties
import com.keeplearning.forge.exception.ForgeException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScimClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var scimClient: ScimClient
    private val objectMapper = jacksonMapperBuilder().build()

    private val realmName = "test-realm"

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val properties = ForgeProperties(
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            realmName = realmName,
            apiVersion = "1.0"
        )

        val webClient = WebClient.builder()
            .baseUrl(properties.baseUrl)
            .build()

        scimClient = ScimClient(webClient, properties)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `listUsers sends correct request`() = runTest {
        val response = ScimListResponse(
            totalResults = 1,
            startIndex = 1,
            itemsPerPage = 1,
            resources = listOf(
                ScimUserResource(
                    id = UUID.randomUUID().toString(),
                    userName = "john@example.com"
                )
            )
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(response))
        )

        val result = scimClient.listUsers(startIndex = 1, count = 50)

        assertEquals(1, result.totalResults)
        assertEquals(1, result.resources.size)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/api/scim/v2/realms/$realmName/Users"))
        assertTrue(request.path!!.contains("startIndex=1"))
        assertTrue(request.path!!.contains("count=50"))
        assertEquals("1.0", request.getHeader("API-Version"))
    }

    @Test
    fun `listUsers with filter includes filter param`() = runTest {
        val response = ScimListResponse(
            totalResults = 0,
            startIndex = 1,
            itemsPerPage = 0,
            resources = emptyList()
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(response))
        )

        scimClient.listUsers(filter = "userName eq \"john@example.com\"")

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("filter="))
    }

    @Test
    fun `getUser sends correct request`() = runTest {
        val userId = UUID.randomUUID()
        val resource = ScimUserResource(
            id = userId.toString(),
            userName = "john@example.com",
            name = ScimName(givenName = "John", familyName = "Doe"),
            active = true
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(resource))
        )

        val result = scimClient.getUser(userId)

        assertEquals(userId.toString(), result.id)
        assertEquals("john@example.com", result.userName)
        assertEquals("John", result.name?.givenName)

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/scim/v2/realms/$realmName/Users/$userId", request.path)
        assertEquals("1.0", request.getHeader("API-Version"))
    }

    @Test
    fun `getUser 404 throws ForgeException`() = runTest {
        val errorResponse = ScimErrorResponse(
            status = "404",
            detail = "User not found"
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(errorResponse))
        )

        val ex = assertThrows<ForgeException> {
            scimClient.getUser(UUID.randomUUID())
        }
        assertEquals(404, ex.status)
        assertNotNull(ex.scimError)
        assertEquals("User not found", ex.scimError!!.detail)
    }

    @Test
    fun `createUser sends POST with body`() = runTest {
        val input = ScimUserResource(
            userName = "new@example.com",
            name = ScimName(givenName = "New", familyName = "User"),
            active = true
        )
        val created = input.copy(id = UUID.randomUUID().toString())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(created))
        )

        val result = scimClient.createUser(input)

        assertNotNull(result.id)
        assertEquals("new@example.com", result.userName)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/scim/v2/realms/$realmName/Users", request.path)
        assertTrue(request.body.readUtf8().contains("new@example.com"))
    }

    @Test
    fun `replaceUser sends PUT with body`() = runTest {
        val userId = UUID.randomUUID()
        val input = ScimUserResource(
            userName = "updated@example.com",
            active = true
        )
        val updated = input.copy(id = userId.toString())

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(updated))
        )

        val result = scimClient.replaceUser(userId, input)

        assertEquals(userId.toString(), result.id)

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/scim/v2/realms/$realmName/Users/$userId", request.path)
    }

    @Test
    fun `patchUser sends PATCH with ScimPatchRequest body`() = runTest {
        val userId = UUID.randomUUID()
        val patchRequest = ScimPatchRequest(
            operations = listOf(
                ScimPatchOperation(op = "replace", path = "displayName", value = "New Name")
            )
        )
        val patched = ScimUserResource(
            id = userId.toString(),
            userName = "john@example.com",
            displayName = "New Name",
            active = true
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(patched))
        )

        val result = scimClient.patchUser(userId, patchRequest)

        assertEquals("New Name", result.displayName)

        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("replace"))
        assertTrue(body.contains("displayName"))
    }

    @Test
    fun `deleteUser sends DELETE`() = runTest {
        val userId = UUID.randomUUID()

        mockWebServer.enqueue(
            MockResponse().setResponseCode(204)
        )

        scimClient.deleteUser(userId)

        val request = mockWebServer.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/scim/v2/realms/$realmName/Users/$userId", request.path)
        assertEquals("1.0", request.getHeader("API-Version"))
    }

    @Test
    fun `error response parsed as ScimErrorResponse`() = runTest {
        val errorResponse = ScimErrorResponse(
            status = "400",
            scimType = "invalidValue",
            detail = "Invalid filter syntax"
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(errorResponse))
        )

        val ex = assertThrows<ForgeException> {
            scimClient.listUsers(filter = "bad filter")
        }
        assertEquals(400, ex.status)
        assertNotNull(ex.scimError)
        assertEquals("invalidValue", ex.scimError!!.scimType)
        assertEquals("Invalid filter syntax", ex.scimError!!.detail)
    }

    @Test
    fun `server error without SCIM body wraps gracefully`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val ex = assertThrows<ForgeException> {
            scimClient.getUser(UUID.randomUUID())
        }
        assertEquals(500, ex.status)
        assertNull(ex.scimError)
    }
}
