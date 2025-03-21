package org.incept5.error

import org.incept5.correlation.CorrelationId
import org.incept5.error.response.CommonError
import org.incept5.error.response.CommonErrorResponse
import org.incept5.error.util.LogEvent
import io.vertx.core.http.HttpServerRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
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
        private val log = Logger.getLogger(RestErrorHandler::class.java)
    }

    /**
     * This is the main handler for all Velostone exceptions
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
                log.error("Unexpected exception encountered : {}", requestLog(req), exp)
            } else {
                // all other exceptions are logged to WARN and the message and first 10 lines of the root cause appear in the logs
                log.warn("Application exception encountered : {}", requestLog(req, exp))
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
            log.error("Failed to generate error response", exp)
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
        return handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, "Request Not Acceptable"))
    }

    @ServerExceptionMapper
    fun handleWebApplicationException(
        req: HttpServerRequest,
        exp: WebApplicationException,
    ): Response {
        return handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp))
    }

    @ServerExceptionMapper
    fun handleConstraintViolationException(
        req: HttpServerRequest,
        exp: ConstraintViolationException,
    ): Response {
        log.warn("Constraint violation : ${requestLog(req)}")
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
        log.error("Unexpected error : {}", requestLog(req), exp)
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

    private fun requestLog(req: HttpServerRequest) : LogEvent {
        return LogEvent(
            "path" to req.path(),
            "method" to req.method(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress(),
        )
    }

    private fun requestLog(req: HttpServerRequest, exp: Throwable) : LogEvent {
        return LogEvent(
            "exception" to ExceptionInfo(exp),
            "path" to req.path(),
            "method" to req.method().name(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress(),
        )
    }

    private fun requestLog(req: HttpServerRequest, exp: CoreException) : LogEvent {
        return LogEvent(
            "exception" to CoreExceptionInfo(exp),
            "path" to req.path(),
            "method" to req.method().name(),
            "query" to req.query(),
            "remoteAddress" to req.remoteAddress().hostAddress(),
        )
    }
}
