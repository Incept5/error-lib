package org.incept5.error.util

object ExceptionUtils {
    fun getCauseLinesFromException(exp: Throwable, limit: Int = 10): List<String> {
        return if (exp.cause != null) {
            getCauseLinesFromException(exp.cause!!)
        } else {
            exp.stackTrace.map { it.toString() }.take(limit)
        }
    }
}
