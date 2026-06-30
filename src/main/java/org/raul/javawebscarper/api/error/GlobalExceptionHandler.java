package org.raul.javawebscarper.api.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.raul.javawebscarper.exception.BadRequestException;
import org.raul.javawebscarper.exception.DuplicateResourceException;
import org.raul.javawebscarper.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
			ResourceNotFoundException exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicate(
			DuplicateResourceException exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.CONFLICT, exception.getMessage(), request, null);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(
			BadRequestException exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request
	) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
			errors.put(violation.getPropertyPath().toString(), violation.getMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
	}

	@ExceptionHandler({
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class
	})
	public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
			Exception exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, "Malformed request", request, null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
			DataIntegrityViolationException exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.CONFLICT, "Request violates data integrity constraints", request, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(
			Exception exception,
			HttpServletRequest request
	) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, null);
	}

	private ResponseEntity<ApiErrorResponse> build(
			HttpStatus status,
			String message,
			HttpServletRequest request,
			Map<String, String> validationErrors
	) {
		ApiErrorResponse response = new ApiErrorResponse(
				OffsetDateTime.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getRequestURI(),
				validationErrors == null || validationErrors.isEmpty() ? null : validationErrors
		);
		return ResponseEntity.status(status).body(response);
	}
}
