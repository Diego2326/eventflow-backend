package com.eventflow.eventflow_api.shared.infrastructure.web

import com.eventflow.eventflow_api.shared.application.error.ApiErrorKind
import com.eventflow.eventflow_api.shared.application.error.ApiException

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

data class ApiErrorBody(val error: ApiError)
data class ApiError(val code: String, val message: String, val details: Map<String, String> = emptyMap(), val timestamp: Instant = Instant.now(), val path: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun api(ex: ApiException, req: HttpServletRequest) = response(
        when (ex.kind) {
            ApiErrorKind.NOT_FOUND -> HttpStatus.NOT_FOUND
            ApiErrorKind.CONFLICT -> HttpStatus.CONFLICT
            ApiErrorKind.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ApiErrorKind.FORBIDDEN -> HttpStatus.FORBIDDEN
            ApiErrorKind.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        }, ex.kind.name, ex.message ?: "Error", req)

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
