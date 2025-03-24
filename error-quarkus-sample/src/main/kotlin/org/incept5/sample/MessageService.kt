package org.incept5.sample

import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class MessageService {
    fun createMessage(createMessageRequest: CreateMessageRequest): MessageResponse {
        return MessageResponse(
            id = UUID.randomUUID(),
            message = createMessageRequest.message,
            code = createMessageRequest.code,
        )
    }

    fun getMessage(messageId: UUID): MessageResponse {
        return MessageResponse(
            id = messageId,
            message = "This is a test message",
            code = "TEST_MESSAGE",
        )
    }
}
