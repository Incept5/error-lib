package org.incept5.error.sample

import jakarta.validation.constraints.NotBlank
import java.net.URL

data class CreateMessageRequest(
    @field:NotBlank
    var message: String? = null,
    var code: String? = null,
    var count: Int? = null,
    var callbackUrl: URL? = null
)
