package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/super/realms/{realmName}/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    suspend fun listUsers(
        @PathVariable realmName: String
    ): List<RealmUserResponse> {
        return userService.listUsers(realmName)
    }

    @GetMapping("/{userId}")
    suspend fun getUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID
    ): RealmUserDetailResponse {
        return userService.getUser(realmName, userId)
    }

    @GetMapping("/by-email/{email}")
    suspend fun getUserByEmail(
        @PathVariable realmName: String,
        @PathVariable email: String
    ): RealmUserDetailResponse {
        return userService.getUserByEmail(realmName, email)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createUser(
        @PathVariable realmName: String,
        @RequestBody request: CreateRealmUserRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RealmUserDetailResponse {
        val actorId = actor.token.subject
        return userService.createUser(realmName, request, actorId)
    }

    @PutMapping("/{userId}")
    suspend fun updateUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateRealmUserRequest
    ): RealmUserDetailResponse {
        return userService.updateUser(realmName, userId, request)
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteUser(
        @PathVariable realmName: String,
        @PathVariable userId: UUID
    ) {
        userService.deleteUser(realmName, userId)
    }

    @PostMapping("/{userId}/clients")
    suspend fun assignUserToClients(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        @RequestBody request: AssignClientsRequest,
        @AuthenticationPrincipal actor: JwtAuthenticationToken
    ): RealmUserDetailResponse {
        val actorId = actor.token.subject
        return userService.assignUserToClients(realmName, userId, request, actorId)
    }

    @DeleteMapping("/{userId}/clients/{clientId}")
    suspend fun removeUserFromClient(
        @PathVariable realmName: String,
        @PathVariable userId: UUID,
        @PathVariable clientId: UUID
    ): RealmUserDetailResponse {
        return userService.removeUserFromClient(realmName, userId, clientId)
    }
}
