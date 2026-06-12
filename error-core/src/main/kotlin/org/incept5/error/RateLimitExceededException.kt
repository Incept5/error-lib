package org.incept5.error

/**
 * Convenience exception for signalling that a caller has exceeded a rate limit.
 *
 * Maps to HTTP 429 (Too Many Requests) in the transport layer. If retryAfterSeconds
 * is supplied it is emitted as the Retry-After response header, otherwise a default
 * is applied by the transport layer.
 *
 * Rate limit errors are transient by nature, so these exceptions are always retryable.
 */
open class RateLimitExceededException(
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : CoreException(
    ErrorCategory.RATE_LIMIT_EXCEEDED,
    listOf(Error(ErrorCategory.RATE_LIMIT_EXCEEDED.name)),
    message,
    cause,
    retryable = true,
)
