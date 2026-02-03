package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.ClientService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/super/realms/{realmName}/clients")
class ClientController(
    private val clientService: ClientService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createClient(
        @PathVariable realmName: String,
        @RequestBody request: CreateClientRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): ClientDetailResponse {
        return clientService.createClient(realmName, request, actor)
    }

    @GetMapping
    suspend fun listClients(@PathVariable realmName: String): List<ClientResponse> {
        return clientService.listClients(realmName)
    }

    @GetMapping("/{clientId}")
    suspend fun getClient(
        @PathVariable realmName: String,
        @PathVariable clientId: String
    ): ClientDetailResponse {
        return clientService.getClient(realmName, clientId)
    }

    @PutMapping("/{clientId}")
    suspend fun updateClient(
        @PathVariable realmName: String,
        @PathVariable clientId: String,
        @RequestBody request: UpdateClientRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): ClientDetailResponse {
        return clientService.updateClient(realmName, clientId, request, actor)
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteClient(
        @PathVariable realmName: String,
        @PathVariable clientId: String,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ) {
        clientService.deleteClient(realmName, clientId, actor)
    }

    @GetMapping("/{clientId}/secret")
    suspend fun getClientSecret(
        @PathVariable realmName: String,
        @PathVariable clientId: String
    ): ClientSecretResponse {
        return clientService.getClientSecret(realmName, clientId)
    }

    @PostMapping("/{clientId}/secret")
    suspend fun regenerateClientSecret(
        @PathVariable realmName: String,
        @PathVariable clientId: String
    ): ClientSecretResponse {
        return clientService.regenerateClientSecret(realmName, clientId)
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createApplication(
        @PathVariable realmName: String,
        @RequestBody request: CreateApplicationRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): ApplicationResponse {
        return clientService.createApplication(realmName, request, actor)
    }
}
