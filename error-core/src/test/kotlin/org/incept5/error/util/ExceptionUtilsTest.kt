package org.incept5.error.util

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private class TargetException(message: String) : RuntimeException(message)

private class WrapperException(message: String, cause: Throwable) : RuntimeException(message, cause)

class ExceptionUtilsTest : ShouldSpec({
    context("findCauseOfType / hasCauseOfType") {
        should("find the throwable itself when it is the target type") {
            val ex = TargetException("boom")
            ex.findCauseOfType(TargetException::class.java) shouldBe ex
            ex.hasCauseOfType(TargetException::class.java) shouldBe true
        }

        should("find a target wrapped several layers deep in the cause chain") {
            val target = TargetException("boom")
            val wrapped = WrapperException("outer", WrapperException("middle", target))

            wrapped.findCauseOfType(TargetException::class.java) shouldBe target
            wrapped.hasCauseOfType(TargetException::class.java) shouldBe true
        }

        should("return null when no cause matches the target type") {
            val wrapped = WrapperException("outer", IllegalStateException("inner"))

            wrapped.findCauseOfType(TargetException::class.java).shouldBeNull()
            wrapped.hasCauseOfType(TargetException::class.java) shouldBe false
        }

        should("not walk beyond maxDepth") {
            // Target sits 3 layers down: wrapper -> wrapper -> wrapper -> target
            val target = TargetException("deep")
            val chain = WrapperException("a", WrapperException("b", WrapperException("c", target)))

            // maxDepth of 2 only visits the first two throwables, so the target is out of reach
            chain.findCauseOfType(TargetException::class.java, maxDepth = 2).shouldBeNull()
            // a larger cap reaches it
            chain.findCauseOfType(TargetException::class.java, maxDepth = 4) shouldBe target
        }

        should("terminate on a cause chain longer than the default cap") {
            // A chain deeper than MAX_CAUSE_DEPTH must still terminate (and not find the target)
            val target = TargetException("very deep")
            var current: Throwable = target
            repeat(MAX_CAUSE_DEPTH + 5) { current = WrapperException("layer", current) }

            current.findCauseOfType(TargetException::class.java).shouldBeNull()
        }

        should("support the reified convenience overloads") {
            val target = TargetException("boom")
            val wrapped = WrapperException("outer", target)

            wrapped.findCauseOfType<TargetException>() shouldBe target
            wrapped.hasCauseOfType<TargetException>() shouldBe true
            wrapped.hasCauseOfType<IllegalArgumentException>() shouldBe false
        }
    }

    context("getCauseLinesFromException") {
        should("return stack-trace lines from the root cause") {
            val root = TargetException("root")
            val wrapped = WrapperException("outer", WrapperException("middle", root))

            val lines = ExceptionUtils.getCauseLinesFromException(wrapped)

            lines shouldBe root.stackTrace.map { it.toString() }.take(10)
        }

        should("honour the limit on the number of lines returned") {
            val ex = TargetException("boom")

            ExceptionUtils.getCauseLinesFromException(ex, limit = 3) shouldHaveAtMostSize 3
        }

        should("terminate on a cause chain longer than the default cap") {
            // Deeper than MAX_CAUSE_DEPTH: must still return without overflowing the stack
            var current: Throwable = TargetException("very deep")
            repeat(MAX_CAUSE_DEPTH + 5) { current = WrapperException("layer", current) }

            ExceptionUtils.getCauseLinesFromException(current) shouldHaveAtMostSize 10
        }
    }
})
