package org.incept5.sample

import org.incept5.error.CoreException
import org.incept5.error.Error
import org.incept5.error.ErrorCategory
import io.quarkus.logging.Log
import jakarta.validation.Valid
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI
import java.util.UUID

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
class MessageResource(val testAppService: MessageService) {
    @POST
    fun createMessage(
        @Valid createMessageRequest: CreateMessageRequest,
    ): Response {
        Log.debug("Invoking Create Message resource $createMessageRequest")

        val response = testAppService.createMessage(createMessageRequest)
        return Response.created(URI("/messages/${response.id}")).build()
    }

    @GET
    @Path("/{messageId}")
    fun getMessages(@PathParam("messageId") messageId: UUID): Response {
        Log.debug("Invoking get Messages resource")

        return Response.ok(testAppService.getMessage(messageId)).build()
    }

    @GET
    @Path("/coreException")
    fun getMessages(): Response {
        throw CoreException(
            message = "This is a test core exception",
            category = ErrorCategory.VALIDATION,
            errors = listOf(
                Error("TEST_ERROR", "location"),
            ),
        )
    }
}
