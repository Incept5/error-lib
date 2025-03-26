package org.incept5.error.response

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.incept5.error.CoreException
import org.incept5.error.Error
import org.incept5.error.ErrorCategory

class ErrorResponseLocationTest : ShouldSpec({
    context("CommonError with location information") {
        should("create CommonError with location") {
            // Create a CommonError with location
            val error = CommonError("Error message", "validation.error", "user.email")
            
            // Verify the location is set correctly
            error.code shouldBe "validation.error"
            error.message shouldBe "Error message"
            error.location shouldBe "user.email"
        }
        
        should("create CommonError with null location") {
            // Create a CommonError with null location
            val error = CommonError("Error message", "validation.error", null)
            
            // Verify the location is null
            error.code shouldBe "validation.error"
            error.message shouldBe "Error message"
            error.location shouldBe null
        }
    }
    
    context("CommonErrorResponse with location information") {
        should("create CommonErrorResponse with errors containing location") {
            // Create errors with location information
            val error1 = CommonError("Email validation failed", "validation.error", "user.email")
            val error2 = CommonError("Name is required", "required.field", "user.name")
            
            // Create a CommonErrorResponse with these errors
            val response = CommonErrorResponse(
                listOf(error1, error2),
                "correlation-123",
                400
            )
            
            // Verify the response contains the correct location information
            response.errors.size shouldBe 2
            
            response.errors[0].code shouldBe "validation.error"
            response.errors[0].location shouldBe "user.email"
            
            response.errors[1].code shouldBe "required.field"
            response.errors[1].location shouldBe "user.name"
        }
    }
})