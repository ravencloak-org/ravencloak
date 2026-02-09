package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.ScimBulkRequest
import com.keeplearning.auth.scim.common.ScimBulkResponse
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scim/v2/realms/{realmName}")
class ScimBulkController(
    private val scimBulkService: ScimBulkService
) {

    @PostMapping("/Bulk", version = "1.0+")
    suspend fun bulk(
        @PathVariable realmName: String,
        @RequestBody request: ScimBulkRequest,
        httpRequest: ServerHttpRequest
    ): ScimBulkResponse {
        val baseUrl = "${httpRequest.uri.scheme}://${httpRequest.uri.authority}"
        return scimBulkService.processBulk(realmName, request, baseUrl)
    }
}
