package com.keeplearning.auth.scim

import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.filters.FilterType

/**
 * Translates SCIM filter expressions to SQL WHERE clauses for R2DBC.
 * Uses the UnboundID SCIM 2 SDK to parse filter strings.
 */
object ScimFilterTranslator {

    data class SqlFilter(
        val whereClause: String,
        val bindings: Map<String, Any>
    )

    private var bindCounter = 0

    private val SCIM_TO_COLUMN = mapOf(
        "username" to "email",
        "emails.value" to "email",
        "name.givenname" to "first_name",
        "name.familyname" to "last_name",
        "displayname" to "display_name",
        "title" to "job_title",
        "externalid" to "keycloak_user_id",
        "active" to "status",
        "meta.created" to "created_at",
        "meta.lastmodified" to "updated_at",
        "phonenumbers.value" to "phone"
    )

    fun translate(filterString: String): SqlFilter {
        bindCounter = 0
        val filter = Filter.fromString(filterString)
        val bindings = mutableMapOf<String, Any>()
        val clause = translateFilter(filter, bindings)
        return SqlFilter(clause, bindings)
    }

    private fun translateFilter(filter: Filter, bindings: MutableMap<String, Any>): String {
        return when (filter.filterType) {
            FilterType.AND -> {
                val parts = filter.combinedFilters.map { translateFilter(it, bindings) }
                parts.joinToString(" AND ") { "($it)" }
            }
            FilterType.OR -> {
                val parts = filter.combinedFilters.map { translateFilter(it, bindings) }
                parts.joinToString(" OR ") { "($it)" }
            }
            FilterType.NOT -> {
                val inner = translateFilter(filter.invertedFilter, bindings)
                "NOT ($inner)"
            }
            FilterType.EQUAL -> translateComparison(filter, "=", bindings)
            FilterType.NOT_EQUAL -> translateComparison(filter, "!=", bindings)
            FilterType.GREATER_THAN -> translateComparison(filter, ">", bindings)
            FilterType.GREATER_OR_EQUAL -> translateComparison(filter, ">=", bindings)
            FilterType.LESS_THAN -> translateComparison(filter, "<", bindings)
            FilterType.LESS_OR_EQUAL -> translateComparison(filter, "<=", bindings)
            FilterType.CONTAINS -> translateLike(filter, bindings, LikeType.CONTAINS)
            FilterType.STARTS_WITH -> translateLike(filter, bindings, LikeType.STARTS_WITH)
            FilterType.ENDS_WITH -> translateLike(filter, bindings, LikeType.ENDS_WITH)
            FilterType.PRESENT -> {
                val column = resolveColumn(filter.attributePath.toString())
                "$column IS NOT NULL"
            }
            else -> throw ScimException(
                status = 400,
                scimType = "invalidFilter",
                detail = "Unsupported filter type: ${filter.filterType}"
            )
        }
    }

    private fun translateComparison(filter: Filter, operator: String, bindings: MutableMap<String, Any>): String {
        val attrPath = filter.attributePath.toString()
        val column = resolveColumn(attrPath)
        val value = filter.comparisonValue.asText()
        val bindKey = nextBindKey()

        // Handle the "active" -> status mapping
        if (attrPath.lowercase() == "active") {
            val statusValue = if (value.toBoolean()) "ACTIVE" else "INACTIVE"
            bindings[bindKey] = statusValue
            return "$column $operator :$bindKey"
        }

        bindings[bindKey] = value
        return "$column $operator :$bindKey"
    }

    private enum class LikeType { CONTAINS, STARTS_WITH, ENDS_WITH }

    private fun translateLike(filter: Filter, bindings: MutableMap<String, Any>, type: LikeType): String {
        val column = resolveColumn(filter.attributePath.toString())
        val value = filter.comparisonValue.asText()
        val bindKey = nextBindKey()

        val pattern = when (type) {
            LikeType.CONTAINS -> "%$value%"
            LikeType.STARTS_WITH -> "$value%"
            LikeType.ENDS_WITH -> "%$value"
        }

        bindings[bindKey] = pattern
        return "$column ILIKE :$bindKey"
    }

    private fun resolveColumn(scimAttr: String): String {
        return SCIM_TO_COLUMN[scimAttr.lowercase()]
            ?: throw ScimException(
                status = 400,
                scimType = "invalidFilter",
                detail = "Unsupported filter attribute: $scimAttr"
            )
    }

    private fun nextBindKey(): String {
        return "p${bindCounter++}"
    }
}
