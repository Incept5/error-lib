package org.incept5.error

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual

class CoreExceptionTest : ShouldSpec({
    context("addMetadata (Suppressed CoreException)") {
        withData(
            TestException("testing 123"),
            IllegalArgumentException("testing 123"),
            Exception("hello world"),
        ) { input ->
            val exception =
                shouldThrowAny {
                    throw input.addMetadata(ErrorCategory.CONFLICT, TestErrors.TEST.toError())
                }
            exception.javaClass shouldBeEqual input.javaClass
            exception.suppressed shouldHaveSize 1
            val suppressed = exception.suppressed.first() as CoreException
            suppressed.cause!!.javaClass shouldBeEqual input.javaClass
            suppressed.message!!.shouldBeEqual(input.message!!)
            suppressed.category shouldBeEqual ErrorCategory.CONFLICT
            suppressed.errors.shouldContainExactly(Error("test.123"))
        }
    }
})

private enum class TestErrors(private val code: String) : ErrorCode {
    TEST("test.123"),
    INVALID_TEST("test.invalid"),
    ;

    override fun getCode(): String {
        return this.code
    }
}

private class TestException(message: String) : RuntimeException(message)
