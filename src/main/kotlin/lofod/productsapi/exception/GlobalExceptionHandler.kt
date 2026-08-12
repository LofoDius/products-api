package lofod.productsapi.exception

import org.bson.types.ObjectId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, ex)

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val message = ex.message.orEmpty()
        if (message.contains("hexString", ignoreCase = true) ||
            message.contains("ObjectId", ignoreCase = true) ||
            isLikelyInvalidObjectId(ex)
        ) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "BAD_REQUEST", message = "Невалидный ObjectId")
            )
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "BAD_REQUEST", message = message.ifBlank { "Некорректный запрос" })
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val root = generateSequence(ex as Throwable) { it.cause }.last()
        if (root is IllegalArgumentException || root.message.orEmpty().contains("ObjectId", ignoreCase = true)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "BAD_REQUEST", message = "Невалидный ObjectId")
            )
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "BAD_REQUEST", message = "Некорректное тело запроса")
        )
    }

    private fun respond(status: HttpStatus, ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(code = ex.code, message = ex.message))

    private fun isLikelyInvalidObjectId(ex: IllegalArgumentException): Boolean {
        val sample = ex.stackTrace.firstOrNull()?.className.orEmpty()
        return sample.contains(ObjectId::class.java.name)
    }
}
