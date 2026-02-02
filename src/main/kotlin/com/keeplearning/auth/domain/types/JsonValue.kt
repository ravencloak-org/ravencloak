package com.keeplearning.auth.domain.types

/**
 * Wrapper class for JSON/JSONB column values.
 * Used to distinguish JSON fields from regular String fields for R2DBC conversion.
 */
@JvmInline
value class JsonValue(val value: String) {
    companion object {
        fun of(value: String?) = value?.let { JsonValue(it) }
        fun empty() = JsonValue("{}")
    }

    fun asString(): String = value

    override fun toString(): String = value
}
