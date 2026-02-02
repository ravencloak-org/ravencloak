package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.IdpService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/super/realms/{realmName}/idp")
class IdpController(
    private val idpService: IdpService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createIdentityProvider(
        @PathVariable realmName: String,
        @RequestBody request: CreateIdpRequest
    ): IdpResponse {
        return idpService.createIdentityProvider(realmName, request)
    }

    @GetMapping
    suspend fun listIdentityProviders(@PathVariable realmName: String): List<IdpResponse> {
        return idpService.listIdentityProviders(realmName)
    }

    @GetMapping("/{alias}")
    suspend fun getIdentityProvider(
        @PathVariable realmName: String,
        @PathVariable alias: String
    ): IdpResponse {
        return idpService.getIdentityProvider(realmName, alias)
    }

    @PutMapping("/{alias}")
    suspend fun updateIdentityProvider(
        @PathVariable realmName: String,
        @PathVariable alias: String,
        @RequestBody request: UpdateIdpRequest
    ): IdpResponse {
        return idpService.updateIdentityProvider(realmName, alias, request)
    }

    @DeleteMapping("/{alias}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteIdentityProvider(
        @PathVariable realmName: String,
        @PathVariable alias: String
    ) {
        idpService.deleteIdentityProvider(realmName, alias)
    }
}
