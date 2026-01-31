package com.keeplearning.keycloak.spi

import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.SubjectCredentialManager
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage

/**
 * Read-only user adapter that maps ExternalUser to Keycloak's UserModel.
 * Extends AbstractUserAdapterFederatedStorage for read-only federated user storage.
 */
class ExternalUserAdapter(
    session: KeycloakSession,
    realm: RealmModel,
    storageProviderModel: ComponentModel,
    private val externalUser: ExternalUser
) : AbstractUserAdapterFederatedStorage(session, realm, storageProviderModel) {

    override fun getId(): String = StorageId.keycloakId(storageProviderModel, externalUser.id)

    override fun getUsername(): String = externalUser.email

    override fun setUsername(username: String?) {
        // Read-only: no-op
    }

    override fun getEmail(): String = externalUser.email

    override fun setEmail(email: String?) {
        // Read-only: no-op
    }

    override fun isEmailVerified(): Boolean = true

    override fun setEmailVerified(verified: Boolean) {
        // Read-only: no-op
    }

    override fun getFirstName(): String? = externalUser.firstName

    override fun setFirstName(firstName: String?) {
        // Read-only: no-op
    }

    override fun getLastName(): String? = externalUser.lastName

    override fun setLastName(lastName: String?) {
        // Read-only: no-op
    }

    override fun isEnabled(): Boolean = true

    override fun setEnabled(enabled: Boolean) {
        // Read-only: no-op
    }

    override fun credentialManager(): SubjectCredentialManager {
        return object : SubjectCredentialManager {
            override fun isValid(credentialTypes: MutableList<org.keycloak.credential.CredentialInput>?): Boolean = false
            override fun updateCredential(input: org.keycloak.credential.CredentialInput?): Boolean = false
            override fun getStoredCredentialsStream(): java.util.stream.Stream<org.keycloak.credential.CredentialModel> = java.util.stream.Stream.empty()
            override fun getStoredCredentialsByTypeStream(type: String?): java.util.stream.Stream<org.keycloak.credential.CredentialModel> = java.util.stream.Stream.empty()
            override fun getStoredCredentialByNameAndType(name: String?, type: String?): org.keycloak.credential.CredentialModel? = null
            override fun getStoredCredentialById(id: String?): org.keycloak.credential.CredentialModel? = null
            override fun isConfiguredFor(type: String?): Boolean = false
            override fun isConfiguredLocally(type: String?): Boolean = false
            override fun getConfiguredUserStorageCredentialTypesStream(): java.util.stream.Stream<String> = java.util.stream.Stream.empty()
            override fun createStoredCredential(cred: org.keycloak.credential.CredentialModel?): org.keycloak.credential.CredentialModel? = null
            override fun updateStoredCredential(cred: org.keycloak.credential.CredentialModel?) {}
            override fun removeStoredCredentialById(id: String?): Boolean = false
            override fun moveStoredCredentialTo(id: String?, newPreviousCredentialId: String?): Boolean = false
            override fun updateCredentialLabel(id: String?, userLabel: String?) {}
            override fun disableCredentialType(type: String?) {}
            override fun getDisableableCredentialTypesStream(): java.util.stream.Stream<String> = java.util.stream.Stream.empty()
            override fun createCredentialThroughProvider(cred: org.keycloak.credential.CredentialModel?): org.keycloak.credential.CredentialModel? = null
        }
    }

    /**
     * Helper object to create Keycloak storage IDs.
     */
    private object StorageId {
        fun keycloakId(model: ComponentModel, externalId: String): String {
            return "f:${model.id}:$externalId"
        }
    }
}
