package org.incept5.error

import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.logging.Log
import io.vertx.core.http.HttpServerRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.incept5.correlation.CorrelationId
import org.incept5.error.response.CommonError
import org.incept5.error.response.CommonErrorResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

/**
 * Handle Errors in a standard way
 *
 * Try to keep the logs uncluttered but informative
 *
 */
@ApplicationScoped
open class RestErrorHandler {

    companion object {
        private val objectMapper = ObjectMapper()
    }

    /**
     * This is the main handler for all CoreException exceptions
     * We convert some others into these below in the handler
     */
    @ServerExceptionMapper
    fun handleCoreException(
        req: HttpServerRequest,
        exp: CoreException,
    ): Response {
        return try {
            if ( exp.category == ErrorCategory.UNEXPECTED ) {
                // unexpected exceptions are logged to ERROR and the full stacktrace appears in the logs
                Log.error("Unexpected exception encountered : ${requestLog(req)}", exp)
            } else {
                // all other exceptions are logged to WARN and the message and first 10 lines of the root cause appear in the logs
                Log.warn("Application exception encountered : ${requestLog(req, exp)}")
            }
            val response =
                CommonErrorResponse(
                    exp.errors.map { CommonError(exp.message!!, it.code, it.location) },
                    CorrelationId.getId(),
                    mapErrorCategoryToHttpStatus(exp.category).statusCode,
                )
            Response
                .status(mapErrorCategoryToHttpStatus(exp.category))
                .entity(response).build()
        } catch (t: Throwable) {
            Log.error("Failed to generate error response", exp)
            handleUnexpectedException(req, t)
        }
    }

    @ServerExceptionMapper
    fun handleNotFoundException(
        req: HttpServerRequest,
        exp: NotFoundException,
    ): Response {
        return handleCoreException(req, toCoreException(ErrorCategory.NOT_FOUND, exp, "Resource Not Found"))
    }

    @ServerExceptionMapper
    fun handleNotSupportedException(
        req: HttpServerRequest,
        exp: NotSupportedException,
    ): Response {
        return handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, "Media Type Not Supported"))
    }

    /**
     * Map 405 to 400
     */
    @ServerExceptionMapper
    fun handleNotAllowedException(
        req: HttpServerRequest,
        exp: NotAllowedException,
    ): Response {
        return handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, "Method Not Allowed"))
    }

    @ServerExceptionMapper
    fun handleNotAcceptableException(
        req: HttpServerRequest,
        exp: NotAcceptableException,
    ): Response {
        val coreException = toCoreException(ErrorCategory.VALIDATION, exp, "Request Not Acceptable")
        Log.warn("Not Acceptable exception encountered : ${requestLog(req, coreException)}")
        
        // Always return JSON response regardless of Accept header
        val response = CommonErrorResponse(
            coreException.errors.map { CommonError(coreException.message!!, it.code, it.location) },
            CorrelationId.getId(),
            Response.Status.BAD_REQUEST.statusCode
        )
        
        return Response
            .status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)  // Force JSON response type
            .entity(response)
            .build()
    }

    @ServerExceptionMapper
    fun handleWebApplicationException(
        req: HttpServerRequest,
        exp: WebApplicationException,
    ): Response {
        // Log the exception details for debugging
        Log.debug("WebApplicationException: ${exp.message}")
        Log.debug("Cause: ${exp.cause?.javaClass?.name}: ${exp.cause?.message}")
        
        // For JSON parsing errors, we need to preserve the original error message
        // The message should already be set correctly in the CustomReaderInterceptor
        val errorMessage = exp.message ?: "Web Application Exception"
        
        return handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, errorMessage))
    }

    @ServerExceptionMapper
    fun handleConstraintViolationException(
        req: HttpServerRequest,
        exp: ConstraintViolationException,
    ): Response {
        Log.warn("Constraint violation : ${requestLog(req)}")
        val errors =
            exp.constraintViolations.map {
                CommonError(it.message, "VALIDATION", it.propertyPath.toString())
            }
        return Response
            .status(Response.Status.BAD_REQUEST)
            .entity(
                CommonErrorResponse(
                    errors,
                    CorrelationId.getId(),
                    Response.Status.BAD_REQUEST.statusCode,
                ),
            )
            .build()
    }

    private fun getCauseFromException(exp: Throwable): String {
        val cause = exp.cause
        val message =
            if (cause != null) {
                //get the first line of the cause message
                if (cause.message != null && cause.message!!.isNotEmpty()) {
                    cause.message!!.split('\n')[0]
                } else {
                    exp.message?: exp.javaClass.simpleName
                }
            } else {
                exp.message?: exp.javaClass.simpleName
            }
        return message
    }

    // see test: db constraint violation
    @ServerExceptionMapper
    fun handleThrowable(
        req: HttpServerRequest,
        exp: Throwable,
    ): Response {
        exp.addMetadata(ErrorCategory.UNEXPECTED, org.incept5.error.Error("suppressed.error"))
        return handleUnexpectedException(req, exp)
    }

    @ServerExceptionMapper
    fun handleRuntimeException(
        req: HttpServerRequest,
        exp: Exception,
    ): Response {
        // check for suppressed exceptions
        return if (exp.suppressed.isNotEmpty()) {
            // if there is a CoreException in the suppressed exceptions, then use that
            // otherwise create a CoreException from the first suppressed exception
            val suppressedCoreException = exp.suppressed.find { it is CoreException } as CoreException?
            if (suppressedCoreException != null) {
                handleCoreException(req, suppressedCoreException)
            } else {
                handleUnexpectedException(req, exp.suppressed.first())
            }
        } else {
            handleUnexpectedException(req, exp)
        }
    }

    private fun handleUnexpectedException(req: HttpServerRequest, exp: Throwable): Response {
        Log.error("Unexpected error : {}", requestLog(req), exp)
        return Response
            .status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(
                CommonErrorResponse(
                    listOf(CommonError(exp.message ?: "Unexpected", "UNEXPECTED", exp.javaClass.simpleName)),
                    CorrelationId.getId(),
                    Response.Status.INTERNAL_SERVER_ERROR.statusCode,
                ),
            )
            .build()
    }

    /**
     * create CoreException from category and throwable
     */
    private fun toCoreException(
        category: ErrorCategory,
        exp: Throwable,
        msg: String = getCauseFromException(exp),
    ) = CoreException(
        category,
        listOf(Error(category.name)),
        msg,
        exp,
    )

    /**
     * Maps the ErrorCategory to the appropriate HTTP status code.
     */
    private fun mapErrorCategoryToHttpStatus(errorCategory: ErrorCategory): Response.Status {
        return when (errorCategory) {
            ErrorCategory.AUTHENTICATION -> Response.Status.UNAUTHORIZED
            ErrorCategory.AUTHORIZATION -> Response.Status.FORBIDDEN
            ErrorCategory.VALIDATION -> Response.Status.BAD_REQUEST
            ErrorCategory.CONFLICT -> Response.Status.CONFLICT
            ErrorCategory.NOT_FOUND -> Response.Status.NOT_FOUND
            ErrorCategory.UNEXPECTED -> Response.Status.INTERNAL_SERVER_ERROR
        }
    }

    private fun requestLog(req: HttpServerRequest) : String {
        val logMap = mapOf(
            "path" to req.path(),
            "method" to req.method(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress()
        )
        return objectMapper.writeValueAsString(logMap)
    }

    private fun requestLog(req: HttpServerRequest, exp: Throwable) : String {
        val logMap = mapOf(
            "exception" to ExceptionInfo(exp),
            "path" to req.path(),
            "method" to req.method().name(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress()
        )
        return objectMapper.writeValueAsString(logMap)
    }

    private fun requestLog(req: HttpServerRequest, exp: CoreException) : String {
        val logMap = mapOf(
            "exception" to CoreExceptionInfo(exp),
            "path" to req.path(),
            "method" to req.method().name(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress()
        )
        return objectMapper.writeValueAsString(logMap)
    }
}
