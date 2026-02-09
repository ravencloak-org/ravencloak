package com.keeplearning.auth.scim

import com.keeplearning.auth.domain.repository.KcRealmRepository
import com.keeplearning.auth.domain.repository.UserRepository
import com.keeplearning.auth.scim.common.ScimChecksumResponse
import com.keeplearning.auth.scim.common.ScimChecksumUtil
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Service

@Service
class ScimChecksumService(
    private val userRepository: UserRepository,
    private val realmRepository: KcRealmRepository
) {

    suspend fun getChecksum(realmName: String, baseUrl: String): ScimChecksumResponse {
        val realm = realmRepository.findByRealmName(realmName).awaitSingleOrNull()
            ?: throw ScimException(404, detail = "Realm '$realmName' not found")

        val users = userRepository.findByRealmId(realm.id!!).collectList().awaitSingle()
        val scimResources = users.map { ScimUserMapper.toScimResource(it, baseUrl) }
        val checksum = ScimChecksumUtil.computeChecksum(scimResources)

        return ScimChecksumResponse(checksum = checksum, userCount = users.size)
    }
}
