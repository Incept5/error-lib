package org.incept5.error.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("errors", "correlationId", "httpStatusCode")
class CommonErrorResponse(
    @JsonProperty("errors")
    val errors: List<CommonError>,
    @JsonProperty("correlationId")
    val correlationId: String,
    @JsonProperty("httpStatusCode")
    val httpStatusCode: Int,
) {
    override fun toString(): String {
        return "CommonErrorResponse(errors=$errors, correlationId='$correlationId', httpStatusCode=$httpStatusCode)"
    }
}
