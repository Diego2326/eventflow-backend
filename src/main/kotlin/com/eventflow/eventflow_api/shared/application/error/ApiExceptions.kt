package com.eventflow.eventflow_api.shared.application.error

enum class ApiErrorKind { NOT_FOUND, CONFLICT, UNAUTHORIZED, FORBIDDEN, BAD_REQUEST }

open class ApiException(message: String, val kind: ApiErrorKind) : RuntimeException(message)
class NotFoundException(message: String) : ApiException(message, ApiErrorKind.NOT_FOUND)
class ConflictException(message: String) : ApiException(message, ApiErrorKind.CONFLICT)
class UnauthorizedException(message: String) : ApiException(message, ApiErrorKind.UNAUTHORIZED)
class ForbiddenException(message: String) : ApiException(message, ApiErrorKind.FORBIDDEN)
class BadRequestException(message: String) : ApiException(message, ApiErrorKind.BAD_REQUEST)
