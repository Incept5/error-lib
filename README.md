# Error Library for Kotlin Applications

A comprehensive error handling library for Kotlin applications, with specific support for Quarkus REST services.

## Modules

This library consists of two main modules:

1. **error-core**: Core functionality for error handling in any Kotlin application
2. **error-quarkus**: Integration with Quarkus for standardized REST API error handling

## Features

- Standardized error representation across your application
- Categorized errors with appropriate HTTP status code mapping
- Support for error location/field information
- Consistent error response format for REST APIs
- Automatic handling of common exceptions
- Support for retryable errors
- Correlation ID inclusion in error responses

## Installation

### Gradle

Add the JitPack repository to your build file:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

Add the dependencies:

```kotlin
// For core functionality only
implementation("com.github.incept5.error-lib:error-core:1.0.0")

// For Quarkus integration
implementation("com.github.incept5.error-lib:error-quarkus:1.0.0")
```

### Maven

Add the JitPack repository to your pom.xml:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependencies:

```xml
<!-- For core functionality only -->
<dependency>
    <groupId>com.github.incept5.error-lib</groupId>
    <artifactId>error-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- For Quarkus integration -->
<dependency>
    <groupId>com.github.incept5.error-lib</groupId>
    <artifactId>error-quarkus</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Core Concepts

#### Error Categories

The library defines several error categories that map to appropriate HTTP status codes:

```kotlin
enum class ErrorCategory {
    AUTHENTICATION,    // 401 Unauthorized
    AUTHORIZATION,     // 403 Forbidden
    VALIDATION,        // 400 Bad Request
    CONFLICT,          // 409 Conflict
    NOT_FOUND,         // 404 Not Found
    UNEXPECTED,        // 500 Internal Server Error
}
```

#### Error Codes

You can define your own error codes by implementing the `ErrorCode` interface:

```kotlin
enum class MyErrorCodes : ErrorCode {
    INVALID_INPUT,
    RESOURCE_NOT_FOUND,
    DUPLICATE_ENTRY;

    override fun getCode(): String = name
}
```

### Using CoreException

You can throw a `CoreException` directly:

```kotlin
throw CoreException(
    category = ErrorCategory.VALIDATION,
    errors = listOf(Error("INVALID_INPUT", "fieldName")),
    message = "Validation failed"
)
```

### Adding Metadata to Exceptions

You can add error metadata to any exception:

```kotlin
val exception = RuntimeException("Something went wrong")
exception.addMetadata(
    category = ErrorCategory.VALIDATION,
    errors = *arrayOf(Error("INVALID_INPUT", "fieldName")),
    retryable = false
)
throw exception
```

### Retryable Errors

You can mark errors as retryable:

```kotlin
throw CoreException(
    category = ErrorCategory.UNEXPECTED,
    errors = listOf(Error("TEMPORARY_FAILURE")),
    message = "Service temporarily unavailable",
    retryable = true
)
```

Check if an exception is retryable:

```kotlin
if (exception.isRetryable()) {
    // Implement retry logic
}
```

## Quarkus Integration

### Setup

1. Add the `error-quarkus` dependency to your project
2. The library will automatically register exception mappers for common exceptions

### Error Response Format

The standard error response format is:

```json
{
  "errors": [
    {
      "message": "Error message",
      "code": "ERROR_CODE",
      "location": "fieldName"
    }
  ],
  "correlationId": "unique-correlation-id",
  "status": 400
}
```

### Example Resource

```kotlin
@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
class MessageResource(val messageService: MessageService) {
    
    @POST
    fun createMessage(@Valid createMessageRequest: CreateMessageRequest): Response {
        try {
            val response = messageService.createMessage(createMessageRequest)
            return Response.created(URI("/messages/${response.id}")).build()
        } catch (e: EntityNotFoundException) {
            // Option 1: Add metadata to existing exception
            e.addMetadata(
                ErrorCategory.NOT_FOUND,
                Error("RESOURCE_NOT_FOUND", "id")
            )
            throw e
        }
    }
    
    @GET
    @Path("/{id}")
    fun getMessage(@PathParam("id") id: UUID): Response {
        val message = messageService.getMessage(id) 
            ?: // Option 2: Throw CoreException directly
            throw CoreException(
                ErrorCategory.NOT_FOUND,
                listOf(Error("MESSAGE_NOT_FOUND", "id")),
                "Message not found"
            )
            
        return Response.ok(message).build()
    }
}
```

### Custom Error Codes

Define your own error codes for better organization:

```kotlin
enum class MessageErrorCodes : ErrorCode {
    MESSAGE_NOT_FOUND,
    INVALID_MESSAGE_FORMAT,
    DUPLICATE_MESSAGE;

    override fun getCode(): String = name
}

// Usage
throw CoreException(
    ErrorCategory.VALIDATION,
    listOf(MessageErrorCodes.INVALID_MESSAGE_FORMAT.toError("content")),
    "Invalid message format"
)
```

## Advanced Usage

### Custom Exception Handling

You can extend the `RestErrorHandler` class to add custom exception handling:

```kotlin
@ApplicationScoped
class CustomErrorHandler : RestErrorHandler() {
    
    @ServerExceptionMapper
    fun handleCustomException(
        req: HttpServerRequest,
        exp: MyCustomException
    ): Response {
        // Custom handling logic
        return handleCoreException(
            req, 
            toCoreException(ErrorCategory.VALIDATION, exp, "Custom error")
        )
    }
}
```

### Integration with Validation

The library automatically handles `ConstraintViolationException` and maps validation errors to the standard error format.

## Sample Application

The `error-quarkus-sample` module provides a complete example of how to use the library in a Quarkus application.

## Requirements

- Java 21 or higher
- Kotlin 1.9 or higher
- For Quarkus integration: Quarkus 3.x

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.