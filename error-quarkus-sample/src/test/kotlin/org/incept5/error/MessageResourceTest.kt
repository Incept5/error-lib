package org.incept5.error

import org.incept5.error.sample.CreateMessageRequest
import org.incept5.error.sample.MessageService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.ws.rs.ClientErrorException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.*

@QuarkusTest
class MessageResourceTest {
    @InjectMock
    lateinit var service: MessageService

    @Test
    fun `create message - happy path`() {
        whenever(service.createMessage(any())).thenCallRealMethod()

        given()
            .body(CreateMessageRequest("Message", "505"))
            .contentType("application/json")
            .`when`()
            .post("/messages")
            .then()
            .statusCode(Response.Status.CREATED.statusCode)
    }

    @Test
    fun `create message - throws NotSupportedException`() {
        val response =
            given()
                .body(CreateMessageRequest("Message", "505"))
                .contentType("text/plain")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Media Type Not Supported", error["message"])
        assertEquals("VALIDATION", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `create message - throws unknown ClientErrorException`() {
        whenever(service.createMessage(any())).thenThrow(ClientErrorException(Response.Status.BAD_REQUEST))

        val response =
            given()
                .body(CreateMessageRequest("Message", "505"))
                .contentType("application/json")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("HTTP 400 Bad Request", error["message"])
        assertEquals("VALIDATION", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `create message - throws ValidationException`() {
        whenever(service.createMessage(any())).thenCallRealMethod()

        val response =
            given()
                .body(CreateMessageRequest("", "404"))
                .contentType("application/json")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("must not be blank", error["message"])
        assertEquals("VALIDATION", error["code"])
        assertEquals("createMessage.createMessageRequest.message", error["location"])
    }

    @Test
    fun `get message by id - happy path`() {
        whenever(service.getMessage(any())).thenCallRealMethod()

        val messageId = UUID.randomUUID()
        val response =
            given()
                .`when`()
                .get("/messages/$messageId")
                .then()
                .statusCode(Response.Status.OK.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertEquals(messageId.toString(), response["id"])
        assertEquals("This is a test message", response["message"])
        assertEquals("TEST_MESSAGE", response["code"])
        assertNull(response["location"])
    }

    @Test
    fun `get message by id - throws NotFoundException`() {
        val response =
            given()
                .`when`()
                .get("/some-random-path")
                .then()
                .statusCode(Response.Status.NOT_FOUND.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(404, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Resource Not Found", error["message"])
        assertEquals("NOT_FOUND", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `get message by id - throws NotAllowedException`() {
        val response =
            given()
                .`when`()
                .get("/messages")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Method Not Allowed", error["message"])
        assertEquals("VALIDATION", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `get message by id - throws any Exception`() {
        whenever(service.getMessage(any())).thenThrow(RuntimeException("Something went wrong"))

        val messageId = UUID.randomUUID()
        val response =
            given()
                .`when`()
                .get("/messages/$messageId")
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(500, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Something went wrong", error["message"])
        assertEquals("UNEXPECTED", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `get message by id - throws a CoreException`() {
        whenever(service.getMessage(any())).thenThrow(
            CoreException(
                ErrorCategory.UNEXPECTED,
                listOf(Error("UNEXPECTED", "some-location")),
                "Something went wrong",
                RuntimeException("Something went wrong"),
                false
            )
        )

        val messageId = UUID.randomUUID()
        val response =
            given()
                .`when`()
                .get("/messages/$messageId")
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(500, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Something went wrong", error["message"])
        assertEquals("UNEXPECTED", error["code"])
        assertEquals("some-location", error["location"])
    }

    @Test
    fun `get message by id - throws Exception with any suppressed error`() {
        val exception = RuntimeException("Something went wrong")
        exception.addSuppressed(RuntimeException("Suppressed error"))
        whenever(service.getMessage(any())).thenThrow(exception)

        val messageId = UUID.randomUUID()
        val response =
            given()
                .`when`()
                .get("/messages/$messageId")
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(500, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Suppressed error", error["message"])
        assertEquals("UNEXPECTED", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `get message by id - throws Exception with suppressed CoreException`() {
        whenever(service.getMessage(any())).thenThrow(
            RuntimeException("Something went wrong").addMetadata(
                ErrorCategory.CONFLICT,
                Error("SUPPRESSED_CONFLICT_ERROR"),
            ),
        )

        val messageId = UUID.randomUUID()
        val response =
            given()
                .`when`()
                .get("/messages/$messageId")
                .then()
                .statusCode(Response.Status.CONFLICT.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(409, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Something went wrong", error["message"])
        assertEquals("SUPPRESSED_CONFLICT_ERROR", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `post message - deserialization error - was expecting a colon to separate field name and value`() {
        val messageBody = """{"message" "foo", "code": "123"}"""
        val expectedErrorMessage = "Unexpected character ('\"' (code 34)): was expecting a colon to separate field name and value"
        invokeAndAssertWebApplicationException(messageBody, expectedErrorMessage)
    }
    @Test
    fun `post message - deserialization error - string where integer is expected`() {
        val messageBody = """{"message": "foo", "code": "123", "count": "abc"}"""
        val expectedErrorMessage = "Cannot deserialize value of type `java.lang.Integer` from String \"abc\": not a valid `java.lang.Integer` value"
        invokeAndAssertWebApplicationException(messageBody, expectedErrorMessage)
    }

    @Test
    fun `post message - deserialization error - bad url`() {
        val messageBody = """{"message": "foo", "code": "123", "callbackUrl": "not_a_url"}"""
        val expectedErrorMessage = "Cannot deserialize value of type `java.net.URL` from String \"not_a_url\": not a valid textual representation, problem: no protocol: not_a_url"
        invokeAndAssertWebApplicationException(messageBody, expectedErrorMessage)
    }

    private fun invokeAndAssertWebApplicationException(messageBody: String, expectedErrorMessage: String) {
        val response =
            given()
                .body(messageBody)
                .contentType("application/json")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(Response.Status.BAD_REQUEST.statusCode)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals(expectedErrorMessage, error["message"])
        assertEquals("VALIDATION", error["code"])
        assertNull(response["location"])
    }
}
