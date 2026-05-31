package org.incept5.error.util

import org.incept5.error.CoreException
import org.incept5.error.ErrorCategory

object ExceptionUtils {
    /**
     * Return up to [limit] stack-trace lines from the root cause of [exp].
     *
     * Walks the cause chain iteratively rather than recursively, bounded by [MAX_CAUSE_DEPTH]
     * and skipping a single-step self-reference, so it terminates even on pathological cyclic
     * chains instead of overflowing the stack.
     */
    fun getCauseLinesFromException(exp: Throwable, limit: Int = 10): List<String> {
        var current: Throwable = exp
        var depth = 0
        while (depth++ < MAX_CAUSE_DEPTH) {
            val cause = current.cause?.takeIf { it !== current } ?: break
            current = cause
        }
        return current.stackTrace.map { it.toString() }.take(limit)
    }
}

/**
 * Upper bound on how far we walk a cause chain. Guards against pathological cyclic
 * chains where the simple `it !== current` self-reference check is not enough.
 */
const val MAX_CAUSE_DEPTH = 10

/**
 * Walk this throwable's cause chain (starting with the throwable itself) and return the first
 * cause that is an instance of [type], or `null` if none is found.
 *
 * A meaningful exception is often wrapped by intermediate layers (e.g. a JTA
 * `RollbackException` -> `ArjunaException` -> `OptimisticLockException`), so a top-level type
 * check is not enough. The walk is bounded by [maxDepth] and skips a single-step self-reference,
 * so it terminates even on cyclic chains.
 */
fun <T : Throwable> Throwable.findCauseOfType(type: Class<T>, maxDepth: Int = MAX_CAUSE_DEPTH): T? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth++ < maxDepth) {
        if (type.isInstance(current)) {
            @Suppress("UNCHECKED_CAST")
            return current as T
        }
        current = current.cause.takeIf { it !== current }
    }
    return null
}

/**
 * Returns `true` if this throwable, or any cause within [maxDepth] steps of its cause chain,
 * is an instance of [type]. See [findCauseOfType].
 */
fun Throwable.hasCauseOfType(type: Class<out Throwable>, maxDepth: Int = MAX_CAUSE_DEPTH): Boolean =
    findCauseOfType(type, maxDepth) != null

/**
 * Reified convenience for [findCauseOfType], e.g. `ex.findCauseOfType<OptimisticLockException>()`.
 */
inline fun <reified T : Throwable> Throwable.findCauseOfType(): T? = findCauseOfType(T::class.java)

/**
 * Reified convenience for [hasCauseOfType], e.g. `ex.hasCauseOfType<OptimisticLockException>()`.
 */
inline fun <reified T : Throwable> Throwable.hasCauseOfType(): Boolean = findCauseOfType<T>() != null

/**
 * Add metadata to an exception by adding a suppressed CoreException
 */
fun Throwable.addMetadata(category: ErrorCategory, vararg errors: org.incept5.error.Error) {
    val coreException = CoreException(category, errors.toList(), this.message ?: "Unknown error", this)
    this.addSuppressed(coreException)
}
