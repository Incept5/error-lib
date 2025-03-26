package org.incept5.error.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("code", "message", "location")
class CommonError(
    @JsonProperty("message")
    val message: String,
    @JsonProperty("code")
    val code: String,
    @JsonProperty("location")
    val location: String?,
) {
    override fun toString(): String {
        return "CommonError(code='$code', message='$message', location=$location)"
    }
}
