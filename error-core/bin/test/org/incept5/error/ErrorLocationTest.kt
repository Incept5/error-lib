package org.incept5.error

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.incept5.error.util.addMetadata

class ErrorLocationTest : ShouldSpec({
    context("Error with location") {
        should("create error with location using string code") {
            val error = Error("test.error", "user.email")
            
            error.code shouldBe "test.error"
            error.location shouldBe "user.email"
            error.arguments shouldBe emptyMap<String, Any>()
        }
        
        should("create error with location using ErrorCode") {
            val error = Error(TestErrorCodes.INVALID_INPUT, "user.name")
            
            error.code shouldBe "invalid.input"
            error.location shouldBe "user.name"
            error.arguments shouldBe emptyMap<String, Any>()
        }
        
        should("create error with location and arguments") {
            val arguments = mapOf("max" to 100, "min" to 1)
            val error = Error(TestErrorCodes.OUT_OF_RANGE, "user.age", arguments)
            
            error.code shouldBe "out.of.range"
            error.location shouldBe "user.age"
            error.arguments shouldBe arguments
        }
    }
    
    context("CoreException with errors containing location") {
        should("preserve location in errors") {
            val error1 = Error("validation.error", "user.email")
            val error2 = Error(TestErrorCodes.INVALID_INPUT, "user.name")
            
            val exception = CoreException(
                ErrorCategory.VALIDATION,
                listOf(error1, error2),
                "Validation failed"
            )
            
            exception.errors shouldBe listOf(error1, error2)
            exception.errors[0].location shouldBe "user.email"
            exception.errors[1].location shouldBe "user.name"
        }
    }
    
    context("Exception with metadata containing location") {
        should("preserve location when adding metadata to exception") {
            val exception = RuntimeException("Something went wrong")
            val errorWithLocation = Error(TestErrorCodes.INVALID_INPUT, "request.id")
            
            exception.addMetadata(ErrorCategory.VALIDATION, errorWithLocation)
            
            val suppressed = exception.suppressed.first()
            suppressed.shouldBeInstanceOf<CoreException>()
            
            val coreException = suppressed as CoreException
            coreException.errors shouldBe listOf(errorWithLocation)
            coreException.errors[0].location shouldBe "request.id"
        }
        
        should("preserve multiple error locations when adding metadata") {
            val exception = IllegalArgumentException("Multiple validation errors")
            val error1 = Error("required.field", "user.firstName")
            val error2 = Error("invalid.format", "user.email")
            val error3 = Error(TestErrorCodes.OUT_OF_RANGE, "user.age", mapOf("max" to 120))
            
            exception.addMetadata(ErrorCategory.VALIDATION, error1, error2, error3)
            
            val suppressed = exception.suppressed.first() as CoreException
            suppressed.errors.size shouldBe 3
            suppressed.errors[0].location shouldBe "user.firstName"
            suppressed.errors[1].location shouldBe "user.email"
            suppressed.errors[2].location shouldBe "user.age"
            suppressed.errors[2].arguments shouldBe mapOf("max" to 120)
        }
    }
})

private enum class TestErrorCodes(private val code: String) : ErrorCode {
    INVALID_INPUT("invalid.input"),
    OUT_OF_RANGE("out.of.range"),
    REQUIRED_FIELD("required.field");
    
    override fun getCode(): String {
        return this.code
    }
}