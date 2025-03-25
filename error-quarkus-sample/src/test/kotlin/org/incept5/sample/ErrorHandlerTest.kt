package org.incept5.sample

import org.incept5.error.CoreException
import org.incept5.error.Error
import org.incept5.error.ErrorCategory
import org.incept5.error.addMetadata
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.NotSupportedException
import jakarta.ws.rs.NotAllowedException
import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.WebApplicationException
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled

@QuarkusTest
class ErrorHandlerTest {

    @Test
    fun `core exception is logged as it should be`() {
        val response =
            given()
                .body(CreateMessageRequest("Message", "505"))
                .contentType("text/plain")
                .`when`()
                .get("/messages/coreException")
                .then()
                .statusCode(400)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("This is a test core exception", error["message"])
        assertEquals("TEST_ERROR", error["code"])
        assertNull(response["location"])
    }

    @Test
    fun `not found exception is handled correctly`() {
        val response =
            given()
                .`when`()
                .get("/non-existent-path")
                .then()
                .statusCode(404)
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
    }

    @Test
    fun `not supported exception is handled correctly`() {
        val response =
            given()
                .contentType("application/xml")
                .body("<xml>test</xml>")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(400)
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
    }

    @Test
    fun `not allowed exception is handled correctly`() {
        val response =
            given()
                .`when`()
                .delete("/messages")
                .then()
                .statusCode(400)
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
    }

    @Test
    fun `constraint violation exception is handled correctly`() {
        val response =
            given()
                .contentType(ContentType.JSON)
                .body("{\"message\": \"\", \"code\": \"invalid\"}")
                .`when`()
                .post("/messages")
                .then()
                .statusCode(400)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertTrue(errors.isNotEmpty())
        
        // Verify at least one validation error exists
        val hasValidationError = errors.any { error ->
            (error as Map<*, *>)["code"] == "VALIDATION"
        }
        assertTrue(hasValidationError)
    }

    @Test
    fun `runtime exception with metadata is handled correctly`() {
        val response =
            given()
                .`when`()
                .get("/messages/runtimeException")
                .then()
                .statusCode(400)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Runtime exception with metadata", error["message"])
        assertEquals("RUNTIME_ERROR", error["code"])
    }

    @Test
    fun `unexpected exception is handled correctly`() {
        val response =
            given()
                .`when`()
                .get("/messages/unexpectedException")
                .then()
                .statusCode(500)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(500, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Unexpected exception", error["message"])
        assertEquals("UNEXPECTED", error["code"])
    }

    @Test
    fun `not acceptable exception is handled correctly`() {
        val response =
            given()
                .header("Accept", "application/xml")
                .`when`()
                .get("/messages/notAcceptable")
                .then()
                .statusCode(400)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Request Not Acceptable", error["message"])
        assertEquals("VALIDATION", error["code"])
    }

    @Test
    fun `web application exception is handled correctly`() {
        val response =
            given()
                .`when`()
                .get("/messages/webApplicationException")
                .then()
                .statusCode(400)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(400, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Web application exception", error["message"])
        assertEquals("VALIDATION", error["code"])
    }

    @Test
    fun `throwable is handled correctly`() {
        val response =
            given()
                .`when`()
                .get("/messages/throwable")
                .then()
                .statusCode(500)
                .extract()
                .path<HashMap<String, Any>>("$")

        assertNotNull(response["errors"])
        assertNotNull(response["correlationId"])
        assertEquals(500, response["httpStatusCode"])

        val errors = response["errors"] as List<*>
        assertEquals(1, errors.size)
        val error = errors[0] as Map<*, *>
        assertEquals("Generic throwable", error["message"])
        assertEquals("UNEXPECTED", error["code"])
    }
}
