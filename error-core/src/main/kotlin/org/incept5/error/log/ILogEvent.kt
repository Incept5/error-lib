package org.incept5.error.log

import org.incept5.json.Json
/**
 * Marker interface which tells our logger to format the object as structured text (usually JSON)
 *
 * With default interface method toStructuredText() that converts the object to a structured text format via JSON
 *
 * The class is free to re-define toStructuredText() if it needs to customize the output to a different format
 *
 */
interface ILogEvent {
    fun toStructuredText(): String {
        return Json.toJson(this)
    }
}
