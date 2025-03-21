package org.incept5.error

import org.incept5.error.util.ExceptionUtils

/**
 * Capture useful information from an exception that can be used in LogEvents
 * and limit the size of the stack trace in the logs.
 */
data class ExceptionInfo(private val exp: Throwable) {
    val message: String = exp.message ?: "No message"
    val cls: String = exp.javaClass.simpleName
    val rootCause: List<String> = ExceptionUtils.getCauseLinesFromException(exp)
}
