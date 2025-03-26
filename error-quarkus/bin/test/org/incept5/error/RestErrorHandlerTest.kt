package org.incept5.error

import org.incept5.error.response.CommonErrorResponse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.vertx.core.http.HttpServerRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path
import jakarta.ws.rs.ClientErrorException
import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.NotAllowedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.NotSupportedException

class RestErrorHandlerTest : StringSpec({

    val mockRequest = mockk<HttpServerRequest>(relaxed = true) {
        every { path() } returns  "/test"
    }

    "handleCoreException should map CoreException to CommonErrorResponse" {
        // Arrange
        val exp = CoreException(ErrorCategory.VALIDATION, listOf(Error("ERR-1000")), "Test exception", RuntimeException("Test exception"))
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleCoreException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "Test exception"
        commonError.code shouldBe "ERR-1000"
    }

    "handleJaxwsNotSupportedException should map NotSupportedException to CommonErrorResponse" {
        // Arrange
        val exp = NotSupportedException("not supported")
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleWebApplicationException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "not supported"
        commonError.code shouldBe "VALIDATION"
    }

    "handleJaxwsNotAllowedException should map NotAllowedException to CommonErrorResponse" {
        // Arrange
        val exp = NotAllowedException("GET")
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleNotAllowedException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "Method Not Allowed"
        commonError.code shouldBe "VALIDATION"
    }

    "handleJaxwsNotAcceptableException should map NotAcceptableException to CommonErrorResponse" {
        // Arrange
        val exp = NotAcceptableException("not acceptable")
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleNotAcceptableException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "Request Not Acceptable"
        commonError.code shouldBe "VALIDATION"
    }

    "handleJaxwsNotFoundException should map NotFoundException to CommonErrorResponse" {
        // Arrange
        val exp = NotFoundException("not found")
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleNotFoundException(mockRequest, exp)

        // Assert
        response.status shouldBe 404
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 404

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "Resource Not Found"
        commonError.code shouldBe "NOT_FOUND"
    }

    "handleRuntimeException should map ClientErrorException to CommonErrorResponse" {
        // Arrange
        val exp = ClientErrorException("client error", 400)
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleThrowable(mockRequest, exp)

        // Assert
        response.status shouldBe 500
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 500

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "client error"
        commonError.code shouldBe "UNEXPECTED"

        // and suppressed exception
        val suppressedException = exp.suppressed.first()
        suppressedException.message shouldBe "client error"
        suppressedException.javaClass shouldBe CoreException::class.java
    }

    "handleRuntimeException should map RuntimeException to CommonErrorResponse" {
        // Arrange
        val exp = RuntimeException("runtime error")
        exp.addMetadata(ErrorCategory.VALIDATION, Error("VALIDATION_ERROR"))
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleRuntimeException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        val commonError = commonErrorResponse.errors.first()
        commonError.message shouldBe "runtime error"
        commonError.code shouldBe "VALIDATION_ERROR"

        // and suppressed exception
        val suppressedException = exp.suppressed.first()
        suppressedException.message shouldBe "runtime error"
        suppressedException.javaClass shouldBe CoreException::class.java
    }

    "handleConstraintViolationException should map ConstraintViolationException to CommonErrorResponse" {
        // Arrange
        // Create a mock ConstraintViolationException with a mocked set of violations
        val violations = mockk<Set<ConstraintViolation<*>>>()
        val violation1 = mockk<ConstraintViolation<*>>()
        val violation2 = mockk<ConstraintViolation<*>>()
        val path1 = mockk<Path>()
        val path2 = mockk<Path>()
        
        // Configure mocks
        every { violations.iterator() } returns listOf(violation1, violation2).iterator()
        every { violations.size } returns 2
        every { violation1.message } returns "must not be blank"
        every { violation2.message } returns "must be greater than 0"
        every { violation1.propertyPath } returns path1
        every { violation2.propertyPath } returns path2
        every { path1.toString() } returns "name"
        every { path2.toString() } returns "age"
        
        val exp = mockk<ConstraintViolationException>()
        every { exp.message } returns "Validation failed"
        every { exp.constraintViolations } returns violations
        
        val handler = RestErrorHandler()

        // Act
        val response = handler.handleConstraintViolationException(mockRequest, exp)

        // Assert
        response.status shouldBe 400
        val commonErrorResponse = response.entity as CommonErrorResponse
        commonErrorResponse.correlationId shouldNotBe null
        commonErrorResponse.httpStatusCode shouldBe 400

        // Should have two errors
        commonErrorResponse.errors.size shouldBe 2
        
        // Verify errors are present with correct values
        val nameError = commonErrorResponse.errors.find { it.location == "name" }
        nameError shouldNotBe null
        nameError!!.message shouldBe "must not be blank"
        nameError.code shouldBe "VALIDATION"
        
        val ageError = commonErrorResponse.errors.find { it.location == "age" }
        ageError shouldNotBe null
        ageError!!.message shouldBe "must be greater than 0"
        ageError.code shouldBe "VALIDATION"
    }

})
