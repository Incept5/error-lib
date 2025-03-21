package org.incept5.error.util

import org.incept5.error.CoreException
import org.incept5.error.ErrorCategory

object ExceptionUtils {
    fun getCauseLinesFromException(exp: Throwable, limit: Int = 10): List<String> {
        return if (exp.cause != null) {
            getCauseLinesFromException(exp.cause!!)
        } else {
            exp.stackTrace.map { it.toString() }.take(limit)
        }
    }
}

/**
 * Add metadata to an exception by adding a suppressed CoreException
 */
fun Throwable.addMetadata(category: ErrorCategory, vararg errors: org.incept5.error.Error) {
    val coreException = CoreException(category, errors.toList(), this.message ?: "Unknown error", this)
    this.addSuppressed(coreException)
}
