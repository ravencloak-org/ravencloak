package com.keeplearning.forge.client

import com.keeplearning.auth.scim.common.*
import com.keeplearning.forge.config.AuthProperties
import com.keeplearning.forge.exception.AuthException
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

        val properties = AuthProperties(
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
    fun `getUser 404 throws AuthException`() = runTest {
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

        val ex = assertThrows<AuthException> {
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

        val ex = assertThrows<AuthException> {
            scimClient.listUsers(filter = "bad filter")
        }
        assertEquals(400, ex.status)
        assertNotNull(ex.scimError)
        assertEquals("invalidValue", ex.scimError!!.scimType)
        assertEquals("Invalid filter syntax", ex.scimError!!.detail)
    }

    @Test
    fun `bulkRequest sends POST to Bulk endpoint`() = runTest {
        val bulkResponse = ScimBulkResponse(
            operations = listOf(
                ScimBulkOperationResponse(method = "POST", bulkId = "u1", status = "201"),
                ScimBulkOperationResponse(method = "PUT", bulkId = "u2", status = "200")
            )
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(bulkResponse))
        )

        val request = ScimBulkRequest(
            operations = listOf(
                ScimBulkOperation(
                    method = "POST",
                    path = "/Users",
                    bulkId = "u1",
                    data = ScimUserResource(userName = "new@example.com", active = true)
                ),
                ScimBulkOperation(
                    method = "PUT",
                    path = "/Users/${UUID.randomUUID()}",
                    bulkId = "u2",
                    data = ScimUserResource(userName = "existing@example.com", active = true)
                )
            )
        )

        val result = scimClient.bulkRequest(request)

        assertEquals(2, result.operations.size)
        assertEquals("201", result.operations[0].status)
        assertEquals("200", result.operations[1].status)

        val httpRequest = mockWebServer.takeRequest()
        assertEquals("POST", httpRequest.method)
        assertEquals("/api/scim/v2/realms/$realmName/Bulk", httpRequest.path)
        assertEquals("1.0", httpRequest.getHeader("API-Version"))
    }

    @Test
    fun `getChecksum sends GET to checksum endpoint`() = runTest {
        val checksumResponse = ScimChecksumResponse(
            checksum = "abc123def456",
            userCount = 42
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(checksumResponse))
        )

        val result = scimClient.getChecksum()

        assertEquals("abc123def456", result.checksum)
        assertEquals(42, result.userCount)

        val httpRequest = mockWebServer.takeRequest()
        assertEquals("GET", httpRequest.method)
        assertEquals("/api/scim/v2/realms/$realmName/Users/checksum", httpRequest.path)
        assertEquals("1.0", httpRequest.getHeader("API-Version"))
    }

    @Test
    fun `server error without SCIM body wraps gracefully`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val ex = assertThrows<AuthException> {
            scimClient.getUser(UUID.randomUUID())
        }
        assertEquals(500, ex.status)
        assertNull(ex.scimError)
    }
}
