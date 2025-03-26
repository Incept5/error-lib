package org.incept5.error

import org.incept5.error.util.ExceptionUtils

/**
 * Capture useful information from an exception that can be used in LogEvents
 * and limit the size of the stack trace in the logs.
 *
 */
data class CoreExceptionInfo(private val exp: CoreException) {
    val category: String = exp.category.name
    val message: String = exp.message ?: "No message"
    val errors: List<Error> = exp.errors
    val retryable: Boolean = exp.retryable
    val cls: String = exp.javaClass.simpleName
    val rootCause: List<String> = ExceptionUtils.getCauseLinesFromException(exp)
}
