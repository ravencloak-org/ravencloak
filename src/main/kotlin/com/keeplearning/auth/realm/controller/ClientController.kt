package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.ClientService
import org.springframework.http.HttpStatus
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
        @RequestBody request: CreateClientRequest
    ): ClientDetailResponse {
        return clientService.createClient(realmName, request)
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
        @RequestBody request: UpdateClientRequest
    ): ClientDetailResponse {
        return clientService.updateClient(realmName, clientId, request)
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteClient(
        @PathVariable realmName: String,
        @PathVariable clientId: String
    ) {
        clientService.deleteClient(realmName, clientId)
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
}
