package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.RealmService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/super/realms")
class RealmController(
    private val realmService: RealmService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createRealm(@RequestBody request: CreateRealmRequest): RealmResponse {
        return realmService.createRealm(request)
    }

    @GetMapping
    suspend fun listRealms(): List<RealmResponse> {
        return realmService.listRealms()
    }

    @GetMapping("/{name}")
    suspend fun getRealm(@PathVariable name: String): RealmDetailResponse {
        return realmService.getRealm(name)
    }

    @PutMapping("/{name}")
    suspend fun updateRealm(
        @PathVariable name: String,
        @RequestBody request: UpdateRealmRequest
    ): RealmResponse {
        return realmService.updateRealm(name, request)
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteRealm(@PathVariable name: String) {
        realmService.deleteRealm(name)
    }

    @PostMapping("/{name}/spi")
    suspend fun enableSpi(
        @PathVariable name: String,
        @RequestBody request: EnableSpiRequest
    ): RealmResponse {
        return realmService.enableSpi(name, request)
    }

    @PostMapping("/{name}/sync")
    suspend fun triggerSync(@PathVariable name: String): SyncResponse {
        return realmService.triggerSync(name)
    }
}
