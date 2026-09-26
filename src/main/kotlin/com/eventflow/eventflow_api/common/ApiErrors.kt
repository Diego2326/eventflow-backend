package com.eventflow.eventflow_api.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

open class ApiException(message: String, val status: HttpStatus) : RuntimeException(message)
class NotFoundException(message: String) : ApiException(message, HttpStatus.NOT_FOUND)
class ConflictException(message: String) : ApiException(message, HttpStatus.CONFLICT)
class UnauthorizedException(message: String) : ApiException(message, HttpStatus.UNAUTHORIZED)
class ForbiddenException(message: String) : ApiException(message, HttpStatus.FORBIDDEN)
class BadRequestException(message: String) : ApiException(message, HttpStatus.BAD_REQUEST)

data class ApiErrorBody(val error: ApiError)
data class ApiError(val code: String, val message: String, val details: Map<String, String> = emptyMap(), val timestamp: Instant = Instant.now(), val path: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun api(ex: ApiException, req: HttpServletRequest) = response(ex.status, ex.status.name, ex.message ?: "Error", req)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException, req: HttpServletRequest): ResponseEntity<ApiErrorBody> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Valor inválido") }
        return ResponseEntity.badRequest().body(ApiErrorBody(ApiError("VALIDATION_ERROR", "Hay datos inválidos", details, path = req.requestURI)))
    }

    @ExceptionHandler(IllegalArgumentException::class, ConstraintViolationException::class)
    fun badRequest(ex: RuntimeException, req: HttpServletRequest) = response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.message ?: "Solicitud inválida", req)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(ex: DataIntegrityViolationException, req: HttpServletRequest) = response(HttpStatus.CONFLICT, "DATA_CONFLICT", "La operación entra en conflicto con datos existentes", req)

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiErrorBody> {
        ex.printStackTrace()
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "No fue posible completar la operación", req)
    }

    private fun response(status: HttpStatus, code: String, message: String, req: HttpServletRequest) =
        ResponseEntity.status(status).body(ApiErrorBody(ApiError(code, message, path = req.requestURI)))
}
