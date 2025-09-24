package org.incept5.error

/**
 * Lose category or type of error, will be mapped to the appropriate representation in the transport layer.
 */
enum class ErrorCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    VALIDATION,
    CONFLICT,
    NOT_FOUND,
    UNPROCESSABLE,
    UNEXPECTED,
}
