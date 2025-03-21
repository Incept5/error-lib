package org.incept5.error.sample

import java.util.UUID

data class MessageResponse(var id: UUID? = null, var message: String? = null, var code: String? = null)
