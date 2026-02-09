package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ScimBulkService(
    private val scimUserService: ScimUserService
) {
    private val logger = LoggerFactory.getLogger(ScimBulkService::class.java)

    suspend fun processBulk(
        realmName: String,
        request: ScimBulkRequest,
        baseUrl: String
    ): ScimBulkResponse {
        val results = request.operations.map { operation ->
            processOperation(realmName, operation, baseUrl)
        }
        return ScimBulkResponse(operations = results)
    }

    private suspend fun processOperation(
        realmName: String,
        operation: ScimBulkOperation,
        baseUrl: String
    ): ScimBulkOperationResponse {
        return try {
            when (operation.method.uppercase()) {
                "POST" -> {
                    val resource = operation.data
                        ?: return errorResponse(operation, "400", "Missing data for POST operation")
                    val created = scimUserService.createUser(realmName, resource, baseUrl)
                    ScimBulkOperationResponse(
                        method = "POST",
                        bulkId = operation.bulkId,
                        location = created.meta?.location,
                        status = "201",
                        response = created
                    )
                }
                "PUT" -> {
                    val resource = operation.data
                        ?: return errorResponse(operation, "400", "Missing data for PUT operation")
                    val userId = extractUserIdFromPath(operation.path)
                        ?: return errorResponse(operation, "400", "Invalid path for PUT operation: ${operation.path}")
                    val updated = scimUserService.replaceUser(realmName, userId, resource, baseUrl)
                    ScimBulkOperationResponse(
                        method = "PUT",
                        bulkId = operation.bulkId,
                        location = updated.meta?.location,
                        status = "200",
                        response = updated
                    )
                }
                else -> errorResponse(operation, "400", "Unsupported method: ${operation.method}")
            }
        } catch (e: ScimException) {
            logger.warn("Bulk operation failed: {} {}", operation.method, operation.bulkId, e)
            ScimBulkOperationResponse(
                method = operation.method,
                bulkId = operation.bulkId,
                status = e.status.toString(),
                response = ScimErrorResponse(
                    status = e.status.toString(),
                    scimType = e.scimType,
                    detail = e.detail
                )
            )
        } catch (e: Exception) {
            logger.error("Unexpected error in bulk operation: {} {}", operation.method, operation.bulkId, e)
            ScimBulkOperationResponse(
                method = operation.method,
                bulkId = operation.bulkId,
                status = "500",
                response = ScimErrorResponse(
                    status = "500",
                    detail = "Internal server error"
                )
            )
        }
    }

    private fun extractUserIdFromPath(path: String?): java.util.UUID? {
        if (path == null) return null
        val parts = path.trimEnd('/').split("/")
        val idStr = parts.lastOrNull() ?: return null
        return try {
            java.util.UUID.fromString(idStr)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun errorResponse(
        operation: ScimBulkOperation,
        status: String,
        detail: String
    ): ScimBulkOperationResponse {
        return ScimBulkOperationResponse(
            method = operation.method,
            bulkId = operation.bulkId,
            status = status,
            response = ScimErrorResponse(status = status, detail = detail)
        )
    }
}
