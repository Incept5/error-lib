package org.incept5.error

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.incept5.error.response.CommonError

/**
 * Tests to verify that location information is properly preserved when mapping between
 * Error and CommonError objects.
 */
class ErrorMappingTest : ShouldSpec({
    context("Mapping Error to CommonError") {
        should("preserve location when mapping Error to CommonError") {
            // Create an Error with location
            val error = Error("validation.error", "user.email", mapOf("format" to "email"))
            
            // Map to CommonError
            val commonError = CommonError("Validation failed", error.code, error.location)
            
            // Verify location is preserved
            commonError.location shouldBe "user.email"
            commonError.code shouldBe "validation.error"
        }
        
        should("handle null location when mapping Error to CommonError") {
            // Create an Error without location
            val error = Error("validation.error")
            
            // Map to CommonError
            val commonError = CommonError("Validation failed", error.code, error.location)
            
            // Verify location is null
            commonError.location shouldBe null
            commonError.code shouldBe "validation.error"
        }
    }
    
    context("Error with different location formats") {
        should("handle dot notation location") {
            val error = Error("validation.error", "user.address.street")
            error.location shouldBe "user.address.street"
        }
        
        should("handle bracket notation location") {
            val error = Error("validation.error", "items[0].name")
            error.location shouldBe "items[0].name"
        }
        
        should("handle complex location paths") {
            val error = Error("validation.error", "orders[0].items[2].price")
            error.location shouldBe "orders[0].items[2].price"
        }
    }
})