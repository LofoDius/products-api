package lofod.productsapi.exception

sealed class ApiException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(message: String) : ApiException("NOT_FOUND", message)

class ForbiddenException(message: String) : ApiException("FORBIDDEN", message)

class ConflictException(message: String) : ApiException("CONFLICT", message)

class UnauthorizedException(message: String) : ApiException("UNAUTHORIZED", message)

class BadRequestException(message: String) : ApiException("BAD_REQUEST", message)
