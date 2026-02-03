package com.keeplearning.keycloak.spi

import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.storage.UserStorageProvider
import org.keycloak.storage.user.UserLookupProvider
import org.keycloak.util.JsonSerialization
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

/**
 * User Storage Provider that validates users against an external REST API.
 * Implements read-only user lookup by email and username.
 */
class ExternalUserStorageProvider(
    private val session: KeycloakSession,
    private val model: ComponentModel
) : UserStorageProvider, UserLookupProvider {

    companion object {
        private val logger: Logger = Logger.getLogger(ExternalUserStorageProvider::class.java.name)
        private const val BASE_URL = "http://auth-backend:8080/api/public/users"
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    override fun close() {
        // No resources to clean up
    }

    override fun getUserById(realm: RealmModel, id: String): UserModel? {
        logger.fine { "getUserById called with id: $id" }

        // Extract the external ID from Keycloak's storage ID format (f:<componentId>:<externalId>)
        val externalId = extractExternalId(id) ?: return null

        // For this implementation, we don't support lookup by ID directly
        // as our external API uses email as the identifier
        return null
    }

    override fun getUserByUsername(realm: RealmModel, username: String): UserModel? {
        logger.fine { "getUserByUsername called with username: $username" }
        // In our system, username is the email
        return getUserByEmail(realm, username)
    }

    override fun getUserByEmail(realm: RealmModel, email: String): UserModel? {
        logger.fine { "getUserByEmail called with email: $email in realm: ${realm.name}" }

        val externalUser = fetchUserFromApi(email, realm.name) ?: return null
        return ExternalUserAdapter(session, realm, model, externalUser)
    }

    /**
     * Fetches user data from the external REST API.
     *
     * @param email The email address to look up
     * @param realmName The Keycloak realm name
     * @return ExternalUser if found (HTTP 200), null if not found (HTTP 404) or on error
     */
    private fun fetchUserFromApi(email: String, realmName: String): ExternalUser? {
        return try {
            val encodedEmail = java.net.URLEncoder.encode(email, Charsets.UTF_8)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/$encodedEmail"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-Realm-Name", realmName)
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            when (response.statusCode()) {
                200 -> {
                    logger.fine { "User found for email: $email" }
                    parseUserResponse(response.body())
                }
                404 -> {
                    logger.fine { "User not found for email: $email" }
                    null
                }
                else -> {
                    logger.warning { "Unexpected response code ${response.statusCode()} for email: $email" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error fetching user from API for email: $email", e)
            null
        }
    }

    /**
     * Parses the JSON response into an ExternalUser using Keycloak's JsonSerialization.
     */
    private fun parseUserResponse(json: String): ExternalUser? {
        return try {
            val node = JsonSerialization.mapper.readTree(json)
            ExternalUser(
                id = node.get("id")?.asText() ?: return null,
                email = node.get("email")?.asText() ?: return null,
                firstName = node.get("firstName")?.asText(),
                lastName = node.get("lastName")?.asText()
            )
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error parsing user JSON response", e)
            null
        }
    }

    /**
     * Extracts the external ID from Keycloak's storage ID format.
     * Format: f:<componentId>:<externalId>
     */
    private fun extractExternalId(keycloakId: String): String? {
        val parts = keycloakId.split(":")
        return if (parts.size >= 3 && parts[0] == "f") {
            parts.drop(2).joinToString(":")
        } else {
            null
        }
    }
}
