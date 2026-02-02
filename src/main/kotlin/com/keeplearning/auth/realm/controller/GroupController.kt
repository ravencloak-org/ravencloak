package com.keeplearning.auth.realm.controller

import com.keeplearning.auth.realm.dto.*
import com.keeplearning.auth.realm.service.GroupService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/super/realms/{realmName}/groups")
class GroupController(
    private val groupService: GroupService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createGroup(
        @PathVariable realmName: String,
        @RequestBody request: CreateGroupRequest
    ): GroupResponse {
        return groupService.createGroup(realmName, request)
    }

    @GetMapping
    suspend fun listGroups(@PathVariable realmName: String): List<GroupResponse> {
        return groupService.listGroups(realmName)
    }

    @GetMapping("/{groupId}")
    suspend fun getGroup(
        @PathVariable realmName: String,
        @PathVariable groupId: String
    ): GroupResponse {
        return groupService.getGroup(realmName, groupId)
    }

    @PutMapping("/{groupId}")
    suspend fun updateGroup(
        @PathVariable realmName: String,
        @PathVariable groupId: String,
        @RequestBody request: UpdateGroupRequest
    ): GroupResponse {
        return groupService.updateGroup(realmName, groupId, request)
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteGroup(
        @PathVariable realmName: String,
        @PathVariable groupId: String
    ) {
        groupService.deleteGroup(realmName, groupId)
    }

    @PostMapping("/{groupId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createSubgroup(
        @PathVariable realmName: String,
        @PathVariable groupId: String,
        @RequestBody request: CreateGroupRequest
    ): GroupResponse {
        return groupService.createSubgroup(realmName, groupId, request)
    }
}
