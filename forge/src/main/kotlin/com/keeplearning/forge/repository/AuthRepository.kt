package com.keeplearning.forge.repository

import com.keeplearning.auth.scim.common.ScimBulkResponse
import com.keeplearning.auth.scim.common.ScimListResponse
import com.keeplearning.auth.scim.common.ScimPatchOperation

interface AuthRepository<T : AuthUser> {

    suspend fun findById(id: String): T?

    suspend fun findByEmail(email: String): T?

    suspend fun findAll(filter: String? = null, startIndex: Int = 1, count: Int = 100): ScimListResponse

    suspend fun create(user: T): T

    suspend fun update(user: T): T

    suspend fun createAll(users: List<T>): ScimBulkResponse

    suspend fun updateAll(users: List<T>): ScimBulkResponse

    suspend fun patch(id: String, operations: List<ScimPatchOperation>): T

    suspend fun delete(id: String)
}

@Deprecated("Renamed to AuthRepository", ReplaceWith("AuthRepository"))
typealias ForgeUserRepository<T> = AuthRepository<T>
