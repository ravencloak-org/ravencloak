package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.ScimChecksumResponse
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scim/v2/realms/{realmName}/Users")
class ScimChecksumController(
    private val scimChecksumService: ScimChecksumService
) {

    @GetMapping("/checksum", version = "1.0+")
    suspend fun getChecksum(
        @PathVariable realmName: String,
        request: ServerHttpRequest
    ): ScimChecksumResponse {
        val baseUrl = "${request.uri.scheme}://${request.uri.authority}"
        return scimChecksumService.getChecksum(realmName, baseUrl)
    }
}
