package org.incept5.error.util

/**
 * Represents a log event that can be logged as structured JSON
 *
 * Usage:
 *
 * logger.info("MyEvent {}", LogEvent("key1" to "value1", "key2" to "value2"))
 *
 */
class LogEvent(
    vararg pairs: Pair<String, Any?>
) : LinkedHashMap<String,Any?>(), ILogEvent {
    init {
        pairs.forEach { put(it.first, it.second) }
    }
}