package org.incept5.error

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext

/**
 This class is responsible for handling invalid format exceptions and returning a 400 response.
 This can happen if the request body is not in the expected format.
 For example, if a field is expected to be an integer but the request body contains a string.
 The request is not handled by the usual ServerExceptionMapper because it is not a server error.
 This Interceptor will catch the exception and throw a WebApplicationException which will be handled by the ServerExceptionMapper.
 */
@Provider
class CustomReaderInterceptor : ReaderInterceptor {

    /**
     * Only intercept reads
     */
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        return try {
            context.proceed()
        } catch (e: Exception) {
            //already a WebApplicationException so just throw it
            if (e is WebApplicationException) {
                throw e
            }
            //wrap the exception in a WebApplicationException and let the ServerExceptionMapper handle it
            throw WebApplicationException("Invalid Format", e)
        }
    }
}
