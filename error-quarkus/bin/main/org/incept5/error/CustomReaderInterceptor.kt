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
     * Intercept reads and handle exceptions
     */
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        return try {
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
    }
}
