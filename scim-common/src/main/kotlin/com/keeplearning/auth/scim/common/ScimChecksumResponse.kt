package com.keeplearning.auth.scim.common

data class ScimChecksumResponse(
    val checksum: String,
    val userCount: Int
)
