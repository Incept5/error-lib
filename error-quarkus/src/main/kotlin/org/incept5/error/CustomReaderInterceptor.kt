package org.incept5.error

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import io.quarkus.logging.Log
import org.xml.sax.SAXParseException
import javax.xml.stream.XMLStreamException

/**
 This class is responsible for handling invalid format exceptions and returning a 400 response.
 This can happen if the request body is not in the expected format.
 For example, if a field is expected to be an integer but the request body contains a string,
 or if XML content is malformed.
 The request is not handled by the usual ServerExceptionMapper because it is not a server error.
 This Interceptor will catch the exception and throw a WebApplicationException which will be handled by the ServerExceptionMapper.
 */
@Provider
class CustomReaderInterceptor : ReaderInterceptor {

    /**
     * Intercept reads and handle exceptions.
     *
     * Return type is `Any?` (not `Any`): `ReaderInterceptorContext.proceed()` returns `null` for
     * an empty request body — the underlying `MessageBodyReader` yields `null` with no exception
     * thrown. Declaring this `: Any` (non-null) made Kotlin's synthetic non-null return check
     * throw `NullPointerException` on an empty body, and because that NPE was thrown on the
     * `return` (outside the `try`/`catch`) it escaped the 400 mapping and surfaced as a raw 500.
     *
     * A `null` result is then mapped to a 400 ("Request body is required").
     *
     * **Limitation — nullable body parameters:** this interceptor is a global `@Provider`, so the
     * 400 applies to *every* JSON endpoint, and it cannot distinguish a Kotlin-nullable body
     * parameter (`body: T?`) from a required one — parameter nullability is not visible at the
     * JAX-RS `MessageBodyReader` layer. An endpoint that genuinely wants to accept an absent body
     * therefore cannot rely on a nullable JSON body parameter alone; it must opt out of this
     * interceptor or model the optional body another way. This is not a regression: before this
     * fix the same scenario produced a raw 500 via the non-null `Any` return, so no caller was
     * ever successfully accepting an empty body through this interceptor.
     */
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any? {
        val body = try {
            context.proceed()
        } catch (e: Exception) {
            Log.debug("Reader interceptor caught exception: ${e.javaClass.name}: ${e.message}")
            Log.debug("Cause: ${e.cause?.javaClass?.name}: ${e.cause?.message}")

            // Already a WebApplicationException so just throw it
            if (e is WebApplicationException) {
                throw e
            }

            // For JSON parsing errors, we need to extract the exact error message from Jackson
            val jacksonError = if (e.javaClass.name.contains("jackson", ignoreCase = true)) {
                e.message
            } else if (e.cause?.javaClass?.name?.contains("jackson", ignoreCase = true) == true) {
                e.cause?.message
            } else {
                null
            }

            if (jacksonError != null) {
                Log.debug("Found Jackson error: $jacksonError")
                // Pass the original Jackson error message
                throw WebApplicationException(jacksonError, e, Response.Status.BAD_REQUEST)
            }

            // Handle other parsing errors
            val errorMessage = when {
                // XML parsing errors
                e is SAXParseException ||
                e is XMLStreamException ||
                e.cause is SAXParseException ||
                e.cause is XMLStreamException ||
                e.message?.contains("xml", ignoreCase = true) == true ||
                e.cause?.message?.contains("xml", ignoreCase = true) == true -> "Malformed XML Content"

                // Other format errors
                else -> "Invalid Format"
            }

            // Wrap the exception in a WebApplicationException and let the ServerExceptionMapper handle it
            throw WebApplicationException(errorMessage, e, Response.Status.BAD_REQUEST)
        }

        // An empty request body deserialises to null (no exception thrown). Map it to 400 here
        // rather than letting it reach the resource method — see the KDoc above.
        if (body == null) {
            Log.debug("Empty request body — returning 400")
            throw WebApplicationException(
                "Request body is required",
                Response.Status.BAD_REQUEST,
            )
        }
        return body
    }
}
