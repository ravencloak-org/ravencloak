package com.keeplearning.auth.exception

import org.slf4j.LoggerFactory
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ServerWebInputException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(
        val timestamp: Instant = Instant.now(),
        val status: Int,
        val error: String,
        val message: String,
        val details: List<FieldError>? = null
    )

    data class FieldError(
        val field: String,
        val message: String
    )

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationErrors(ex: WebExchangeBindException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.map {
            FieldError(it.field, it.defaultMessage ?: "Invalid value")
        }

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Request validation failed. Check 'details' for specific errors.",
            details = fieldErrors
        )

        logger.warn("Validation error: {}", fieldErrors)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleInputException(ex: ServerWebInputException): ResponseEntity<ErrorResponse> {
        val message = extractReadableMessage(ex)

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = message
        )

        logger.warn("Input error: {}", message)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(DecodingException::class)
    fun handleDecodingException(ex: DecodingException): ResponseEntity<ErrorResponse> {
        val message = extractReadableMessage(ex)

        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Invalid Request Body",
            message = message
        )

        logger.warn("Decoding error: {}", message)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid argument"
        )

        logger.warn("Illegal argument: {}", ex.message)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message ?: "Resource not found"
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClientException(ex: WebClientResponseException): ResponseEntity<ErrorResponse> {
        val message = when (ex.statusCode.value()) {
            401 -> "Authentication failed with Keycloak. Check admin credentials."
            403 -> "Not authorized to perform this action in Keycloak."
            404 -> "Resource not found in Keycloak."
            409 -> "Resource already exists in Keycloak. ${extractKeycloakErrorMessage(ex)}"
            else -> "Keycloak API error: ${extractKeycloakErrorMessage(ex)}"
        }

        val response = ErrorResponse(
            status = ex.statusCode.value(),
            error = ex.statusText,
            message = message
        )

        logger.warn("Keycloak API error: {} - {}", ex.statusCode, message)
        return ResponseEntity.status(ex.statusCode).body(response)
    }

    private fun extractKeycloakErrorMessage(ex: WebClientResponseException): String {
        return try {
            val body = ex.responseBodyAsString
            // Try to extract errorMessage from Keycloak JSON response
            val errorMessagePattern = """"errorMessage"\s*:\s*"([^"]+)"""".toRegex()
            errorMessagePattern.find(body)?.groupValues?.get(1)
                ?: body.take(200)
        } catch (e: Exception) {
            ex.message ?: "Unknown error"
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)

        val response = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred. Please try again later."
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }

    private fun extractReadableMessage(ex: Throwable): String {
        val cause = ex.cause ?: ex
        val message = cause.message ?: return "Invalid request format"

        // Extract field name from Kotlin null safety errors
        val nullFieldPattern = """for JSON property (\w+) due to missing""".toRegex()
        nullFieldPattern.find(message)?.let { match ->
            val field = match.groupValues[1]
            return "Missing required field: '$field'"
        }

        // Extract field name from type mismatch errors
        val typeMismatchPattern = """Cannot deserialize value of type .* from (\w+)""".toRegex()
        typeMismatchPattern.find(message)?.let {
            return "Invalid value type in request body"
        }

        // Extract unknown property errors
        val unknownPropertyPattern = """Unrecognized field "(\w+)"""".toRegex()
        unknownPropertyPattern.find(message)?.let { match ->
            val field = match.groupValues[1]
            return "Unknown field: '$field'"
        }

        // Extract JSON parse errors
        if (message.contains("Unexpected character") || message.contains("JsonParseException")) {
            return "Invalid JSON format"
        }

        // Default: return a sanitized version of the message
        return message.substringBefore("\n").take(200)
    }
}
