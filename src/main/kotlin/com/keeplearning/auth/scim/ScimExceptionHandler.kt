package com.keeplearning.auth.scim

import com.keeplearning.auth.scim.common.ScimErrorResponse
import com.unboundid.scim2.common.exceptions.ScimException as UnboundIdScimException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["com.keeplearning.auth.scim"])
@Order(1) // Higher priority than GlobalExceptionHandler
class ScimExceptionHandler {

    private val logger = LoggerFactory.getLogger(ScimExceptionHandler::class.java)

    @ExceptionHandler(ScimException::class)
    fun handleScimException(ex: ScimException): ResponseEntity<ScimErrorResponse> {
        logger.warn("SCIM error: {} - {}", ex.status, ex.detail)
        val response = ScimErrorResponse(
            status = ex.status.toString(),
            scimType = ex.scimType,
            detail = ex.detail
        )
        return ResponseEntity.status(ex.status).body(response)
    }

    @ExceptionHandler(UnboundIdScimException::class)
    fun handleUnboundIdScimException(ex: UnboundIdScimException): ResponseEntity<ScimErrorResponse> {
        logger.warn("SCIM SDK error: {}", ex.message)
        val response = ScimErrorResponse(
            status = "400",
            scimType = "invalidFilter",
            detail = ex.message
        )
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ScimErrorResponse> {
        logger.warn("SCIM bad request: {}", ex.message)
        val response = ScimErrorResponse(
            status = "400",
            scimType = "invalidValue",
            detail = ex.message
        )
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ScimErrorResponse> {
        logger.error("Unexpected SCIM error", ex)
        val response = ScimErrorResponse(
            status = "500",
            detail = "An unexpected error occurred"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
