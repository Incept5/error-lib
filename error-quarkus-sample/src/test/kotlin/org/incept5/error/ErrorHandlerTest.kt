package org.incept5.error

import org.incept5.sample.CreateMessageRequest
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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


}
