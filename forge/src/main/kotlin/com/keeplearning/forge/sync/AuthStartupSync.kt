package com.keeplearning.forge.sync

import com.keeplearning.forge.repository.AuthUser

abstract class AuthStartupSync<T : Any> {
    abstract suspend fun fetchAllLocalUsers(): List<T>
    abstract fun mapToAuthUser(localUser: T): AuthUser
}
