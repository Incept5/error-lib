package org.incept5.error

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpMethod
import io.vertx.core.net.SocketAddress
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestErrorHandlerTest {

    private lateinit var restErrorHandler: RestErrorHandler
    private lateinit var mockRequest: HttpServerRequest
    private lateinit var mockAddress: SocketAddress

    @BeforeEach
    fun setup() {
        restErrorHandler = RestErrorHandler()
        mockRequest = mockk()
        mockAddress = mockk()
        
        // Setup common mock behaviors
        every { mockRequest.path() } returns "/api/test"
        every { mockRequest.method() } returns HttpMethod.POST
        every { mockRequest.query() } returns null
        every { mockRequest.remoteAddress() } returns mockAddress
        every { mockAddress.hostAddress() } returns "127.0.0.1"
    }

    @Test
    fun `handleInvalidFormatException should return detailed enum error message`() {
        // Create a mock InvalidFormatException for enum deserialization
        val targetType = TestEnum::class.java
        val invalidValue = "INVALID_VALUE"
        val path = JsonMappingException.Reference("currency", "currency")
        val location = JsonLocation.NA
        
        val exception = InvalidFormatException.from(
            null,
            "Cannot deserialize value of type `${targetType.name}` from String \"$invalidValue\": not one of the values accepted for Enum class: [${TestEnum.values().joinToString(", ")}]",
            invalidValue,
            targetType
        )
        exception.prependPath(path)
        
        val response = restErrorHandler.handleInvalidFormatException(mockRequest, exception)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("currency", entity.errors[0].location)
        assertTrue(entity.errors[0].message.contains("Invalid value for currency: INVALID_VALUE"))
        assertTrue(entity.errors[0].message.contains("Must be one of: VALUE1, VALUE2, VALUE3"))
    }

    @Test
    fun `handleInvalidFormatException should handle non-enum type errors`() {
        // Create a mock InvalidFormatException for non-enum type
        val targetType = Integer::class.java
        val invalidValue = "not_a_number"
        val path = JsonMappingException.Reference("amount", "amount")
        
        val exception = InvalidFormatException.from(
            null,
            "Cannot deserialize value of type `java.lang.Integer` from String \"$invalidValue\"",
            invalidValue,
            targetType
        )
        exception.prependPath(path)
        
        val response = restErrorHandler.handleInvalidFormatException(mockRequest, exception)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("amount", entity.errors[0].location)
        assertTrue(entity.errors[0].message.contains("Invalid value 'not_a_number' for field 'amount' of type Integer"))
    }

    @Test
    fun `handleJsonProcessingException should detect enum deserialization errors from IllegalArgumentException`() {
        // Create a JsonMappingException wrapping an IllegalArgumentException (as happens with generated enum fromValue)
        val cause = IllegalArgumentException("Unexpected value 'INVALID_CURRENCY'")
        val path = JsonMappingException.Reference("currency", "currency")
        
        val exception = JsonMappingException(null, "Could not resolve type", cause)
        exception.prependPath(path)
        
        val response = restErrorHandler.handleJsonProcessingException(mockRequest, exception)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("currency", entity.errors[0].location)
        assertTrue(entity.errors[0].message.contains("Invalid value for currency: INVALID_CURRENCY"))
        // Should include hint for currency field
        assertTrue(entity.errors[0].message.contains("Must be one of: USD"))
    }

    @Test
    fun `handleJsonProcessingException should handle regular JsonMappingException`() {
        // Create a regular JsonMappingException without enum-related cause
        val cause = RuntimeException("Some other error")
        val path = JsonMappingException.Reference("field", "field")
        
        val exception = JsonMappingException(null, "Generic mapping error", cause)
        exception.prependPath(path)
        
        val response = restErrorHandler.handleJsonProcessingException(mockRequest, exception)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("field", entity.errors[0].location)
        assertEquals("JSON mapping error at field: field", entity.errors[0].message)
    }

    @Test
    fun `handleBadRequestException should delegate to InvalidFormatException handler when appropriate`() {
        // Create a BadRequestException with InvalidFormatException as cause
        val targetType = TestEnum::class.java
        val invalidValue = "INVALID"
        val path = JsonMappingException.Reference("status", "status")
        
        val invalidFormatEx = InvalidFormatException.from(
            null,
            "Cannot deserialize",
            invalidValue,
            targetType
        )
        invalidFormatEx.prependPath(path)
        
        val badRequestEx = BadRequestException("Bad request", invalidFormatEx)
        
        val response = restErrorHandler.handleBadRequestException(mockRequest, badRequestEx)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("status", entity.errors[0].location)
        assertTrue(entity.errors[0].message.contains("Invalid value for status: INVALID"))
    }

    @Test
    fun `handleWebApplicationException should delegate to InvalidFormatException handler when appropriate`() {
        // Create a WebApplicationException with InvalidFormatException as cause
        val targetType = TestEnum::class.java
        val invalidValue = "WRONG"
        val path = JsonMappingException.Reference("type", "type")
        
        val invalidFormatEx = InvalidFormatException.from(
            null,
            "Cannot deserialize",
            invalidValue,
            targetType
        )
        invalidFormatEx.prependPath(path)
        
        val webAppEx = WebApplicationException("Web app error", invalidFormatEx, Response.Status.BAD_REQUEST)
        
        val response = restErrorHandler.handleWebApplicationException(mockRequest, webAppEx)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals(1, entity.errors.size)
        assertEquals("VALIDATION", entity.errors[0].code)
        assertEquals("type", entity.errors[0].location)
        assertTrue(entity.errors[0].message.contains("Invalid value for type: WRONG"))
    }

    @Test
    fun `handleJsonProcessingException with nested array path`() {
        // Test with array index in path
        val cause = IllegalArgumentException("Unexpected value 'INVALID'")
        val path1 = JsonMappingException.Reference(null, 0) // Array index
        val path2 = JsonMappingException.Reference("items", "items")
        val path3 = JsonMappingException.Reference("currency", "currency")
        
        val exception = JsonMappingException(null, "Could not resolve", cause)
        exception.prependPath(path1)
        exception.prependPath(path2)
        exception.prependPath(path3)
        
        val response = restErrorHandler.handleJsonProcessingException(mockRequest, exception)
        
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        
        val entity = response.entity as org.incept5.error.response.CommonErrorResponse
        assertEquals("currency.items.[0]", entity.errors[0].location)
    }

    // Test enum for use in tests
    enum class TestEnum {
        VALUE1, VALUE2, VALUE3
    }
}