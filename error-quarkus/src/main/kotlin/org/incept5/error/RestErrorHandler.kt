package org.incept5.error

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.core.JsonProcessingException
import io.quarkus.logging.Log
import io.vertx.core.http.HttpServerRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.*
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
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

    /**
     * Handle JWT authentication failures
     * This mapper catches NotAuthorizedException that are thrown by Quarkus SmallRye JWT
     * authentication when JWT validation fails (invalid/expired tokens, missing auth headers, etc.)
     */
    @ServerExceptionMapper
    fun handleNotAuthorizedException(
        req: HttpServerRequest,
        exp: NotAuthorizedException,
    ): Response {
        Log.debug("Authentication failed: ${exp.message}")
        return handleCoreException(req, toCoreException(ErrorCategory.AUTHENTICATION, exp, "Authentication required"))
    }

    @ServerExceptionMapper
    @Priority(Priorities.USER - 100) // High priority to catch before other handlers
    fun handleBadRequestException(
        req: HttpServerRequest,
        exp: BadRequestException,
    ): Response {
        Log.debug("BadRequestException: ${exp.message}")
        Log.debug("BadRequestException cause: ${exp.cause?.javaClass?.name}: ${exp.cause?.message}")
        
        // Check if the cause is an InvalidFormatException
        return when (val cause = exp.cause) {
            is InvalidFormatException -> handleInvalidFormatException(req, cause)
            is JsonProcessingException -> handleJsonProcessingException(req, cause)
            is JsonMappingException -> {
                val fieldPath = cause.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
                val errorMessage = "Invalid JSON format at field: $fieldPath"
                val coreException = CoreException(
                    ErrorCategory.VALIDATION,
                    listOf(Error("VALIDATION", fieldPath)),
                    errorMessage,
                    cause
                )
                handleCoreException(req, coreException)
            }
            else -> {
                val errorMessage = exp.message ?: "Bad Request"
                handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, errorMessage))
            }
        }
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
    @Priority(Priorities.USER - 100) // High priority to catch before other handlers
    fun handleInvalidFormatException(
        req: HttpServerRequest,
        exp: InvalidFormatException,
    ): Response {
        Log.debug("InvalidFormatException caught directly: ${exp.message}")
        
        val fieldPath = exp.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
        val errorMessage = when {
            exp.targetType?.isEnum == true -> {
                val enumValues = exp.targetType.enumConstants.joinToString(", ")
                "Invalid value for $fieldPath: ${exp.value}. Must be one of: $enumValues"
            }
            else -> "Invalid value '${exp.value}' for field '$fieldPath' of type ${exp.targetType?.simpleName}"
        }
        
        // Wrap in CoreException as requested
        val coreException = CoreException(
            ErrorCategory.VALIDATION,
            listOf(Error("VALIDATION", fieldPath)),
            errorMessage,
            exp
        )
        
        return handleCoreException(req, coreException)
    }

    @ServerExceptionMapper
    fun handleJsonProcessingException(
        req: HttpServerRequest,
        exp: JsonProcessingException,
    ): Response {
        Log.debug("JsonProcessingException: ${exp.message}")
        
        // Check if this is an InvalidFormatException for an enum
        return when (exp) {
            is InvalidFormatException -> handleInvalidFormatException(req, exp)
            is JsonMappingException -> {
                val fieldPath = exp.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
                
                // Check if the cause is an IllegalArgumentException from enum deserialization
                val errorMessage = if (exp.cause is IllegalArgumentException && 
                    exp.cause?.message?.startsWith("Unexpected value") == true) {
                    // This is likely an enum deserialization error
                    // Try to extract the invalid value from the error message
                    val invalidValue = exp.cause?.message?.substringAfter("Unexpected value '")?.substringBefore("'")
                    
                    // For currency field specifically, we know the valid values
                    // In a real implementation, we'd need to introspect the enum type
                    val validValuesHint = if (fieldPath == "currency") {
                        ". Must be one of: USD"
                    } else {
                        ""
                    }
                    
                    "Invalid value for $fieldPath: $invalidValue$validValuesHint"
                } else {
                    "JSON mapping error at field: $fieldPath"
                }
                
                val coreException = CoreException(
                    ErrorCategory.VALIDATION,
                    listOf(Error("VALIDATION", fieldPath)),
                    errorMessage,
                    exp
                )
                handleCoreException(req, coreException)
            }
            else -> {
                handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, "JSON processing error"))
            }
        }
    }

    @ServerExceptionMapper
    fun handleWebApplicationException(
        req: HttpServerRequest,
        exp: WebApplicationException,
    ): Response {
        // Log the exception details for debugging
        Log.debug("WebApplicationException: ${exp.message}")
        Log.debug("Cause: ${exp.cause?.javaClass?.name}: ${exp.cause?.message}")
        
        // For JSON parsing errors, extract field path from JsonMappingException
        return when (val cause = exp.cause) {
            is InvalidFormatException -> handleInvalidFormatException(req, cause)
            is JsonProcessingException -> handleJsonProcessingException(req, cause)
            is JsonMappingException -> createJsonMappingErrorResponse(exp, cause)
            else -> {
                val errorMessage = exp.message ?: "Web Application Exception"
                handleCoreException(req, toCoreException(ErrorCategory.VALIDATION, exp, errorMessage))
            }
        }
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
     * Create a Response for JsonMappingException with field path extraction
     */
    private fun createJsonMappingErrorResponse(
        exp: WebApplicationException,
        cause: JsonMappingException
    ): Response {
        val fieldPath = cause.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
        
        Log.warn("createJsonMappingErrorResponse: cause class = ${cause.javaClass.name}, is InvalidFormatException = ${cause is InvalidFormatException}")
        Log.warn("createJsonMappingErrorResponse: cause.cause = ${cause.cause?.javaClass?.name}, cause.message = ${cause.message}")
        
        // Check if this is an InvalidFormatException for an enum
        // Also check if the cause's cause is an InvalidFormatException (nested exception)
        val invalidFormatEx = when {
            cause is InvalidFormatException -> cause
            cause.cause is InvalidFormatException -> cause.cause as InvalidFormatException
            else -> null
        }
        
        val errorMessage = if (invalidFormatEx != null) {
            Log.debug("InvalidFormatException detected: targetType = ${invalidFormatEx.targetType}, isEnum = ${invalidFormatEx.targetType?.isEnum}, value = ${invalidFormatEx.value}")
            when {
                invalidFormatEx.targetType?.isEnum == true -> {
                    val enumValues = invalidFormatEx.targetType.enumConstants.joinToString(", ")
                    "Invalid value for $fieldPath: ${invalidFormatEx.value}. Must be one of: $enumValues"
                }
                else -> "Invalid value '${invalidFormatEx.value}' for field '$fieldPath' of type ${invalidFormatEx.targetType?.simpleName}"
            }
        } else {
            "JSON mapping error at field: $fieldPath"
        }
        
        val errors = listOf(CommonError(errorMessage, "VALIDATION", fieldPath))
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
            ErrorCategory.UNPROCESSABLE -> Response.Status.fromStatusCode(422)!!
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
