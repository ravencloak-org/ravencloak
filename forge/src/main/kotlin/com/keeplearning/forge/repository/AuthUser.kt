package com.keeplearning.forge.repository

import java.time.Instant

abstract class AuthUser {
    open var id: String? = null
    open var externalId: String? = null
    open var email: String = ""
    open var firstName: String? = null
    open var lastName: String? = null
    open var displayName: String? = null
    open var phone: String? = null
    open var jobTitle: String? = null
    open var active: Boolean = true
    open var createdAt: Instant? = null
    open var updatedAt: Instant? = null
}

@Deprecated("Renamed to AuthUser", ReplaceWith("AuthUser"))
typealias ForgeUser = AuthUser
