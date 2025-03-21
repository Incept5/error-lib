package org.incept5.error

import org.incept5.error.util.ExceptionUtils

/**
 * Main exception holding error state for the transport layer.
 *
 * May be use directly or extended, or used non-intrusively via the Throwable.addMetadata() extension.
 *
 * Retryable exceptions are intended to be used when the error is transient and retrying the operation may succeed.
 */
open class CoreException(
    val category: ErrorCategory,
    val errors: List<Error>,
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : RuntimeException(message, cause) {
    init {
        require(errors.isNotEmpty()) { "At least one error must be supplied" }
    }
}

/**
 * Interface intended to be implemented by enums that allows the enum to be mapped to a string representation
 * of said error code.
 */
interface ErrorCode {
    fun getCode(): String

    /**
     * Helper method for enums that implement ErrorCode to transform them into Errors.
     */
    fun toError(): Error {
        return Error(this.getCode())
    }

    /**
     * Helper method for enums that implement ErrorCode to transform them into Errors.
     */
    fun toError(arguments: Map<String, *>): Error {
        return Error(this.getCode(), null, arguments)
    }
}

/**
 * Data class to represent a single error condition and it's arguments, optionally contains the location or field
 * that triggered the error.
 *
 * The error code and arguments will be used to interpolate the error message returned in the transport layer.
 */
data class Error(
    val code: String,
    val location: String?,
    val arguments: Map<String, *>,
) {
    constructor(code: String) : this(code, null, emptyMap<String, Any>())
    constructor(code: String, location: String) : this(code, location, emptyMap<String, Any>())
    constructor(code: ErrorCode) : this(code.getCode(), null, emptyMap<String, Any>())
    constructor(code: ErrorCode, location: String?) : this(code.getCode(), location, emptyMap<String, Any>())
    constructor(code: ErrorCode, location: String?, arguments: Map<String, Any>) : this(code.getCode(), location, arguments)
}

/**
 * Helper method for Throwables to add metadata to them in the form of a suppressed exception, this additional metadata will
 * be used to map the exception to the correct transport layer error code.
 */
fun <T : Throwable> T.addMetadata(
    category: ErrorCategory,
    vararg errors: Error,
    retryable: Boolean = false,
): T {
    this.addSuppressed(CoreException(category, errors.toList(), this.message ?: this.javaClass.simpleName, this, retryable))
    return this
}

/**
 * Helper method to see if any Throwable is retryable.
 * If the throwable contains suppressed exceptions, it will check the first suppressed exception.
 */
fun <T : Throwable> T.isRetryable(): Boolean {
    return when {
        this is CoreException -> this.retryable
        suppressed.isEmpty() -> false
        else -> suppressed.first().isRetryable()
    }
}
