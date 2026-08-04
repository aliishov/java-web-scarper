package org.raul.javawebscarper.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.dto.common.BaseResponseDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "org.raul.javawebscarper.controller")
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.computeIfAbsent(fieldError.getField(), ignored -> new ArrayList<>())
					.add(fieldError.getDefaultMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "Validation failed", errors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleConstraintViolation(ConstraintViolationException exception) {
		Map<String, List<String>> errors = new LinkedHashMap<>();
		for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
			errors.computeIfAbsent(violation.getPropertyPath().toString(), ignored -> new ArrayList<>())
					.add(violation.getMessage());
		}
		return build(HttpStatus.BAD_REQUEST, "Validation failed", errors);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleNotFound(ResourceNotFoundException exception) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), null);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleDuplicate(DuplicateResourceException exception) {
		return build(HttpStatus.CONFLICT, exception.getMessage(), null);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleBadRequest(BadRequestException exception) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleUnauthorized(UnauthorizedException exception) {
		return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), null);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleForbidden(ForbiddenException exception) {
		return build(HttpStatus.FORBIDDEN, exception.getMessage(), null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
		return build(HttpStatus.CONFLICT, "Request violates data integrity constraints", null);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return build(HttpStatus.BAD_REQUEST, "Malformed request body", null);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return build(HttpStatus.BAD_REQUEST, "Invalid request parameter", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<BaseResponseDTO<Void>> handleUnexpected(Exception exception) {
		log.error("Unexpected API error", exception);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
	}

	private ResponseEntity<BaseResponseDTO<Void>> build(
			HttpStatus status,
			String message,
			Map<String, List<String>> errors
	) {
		return ResponseEntity.status(status).body(BaseResponseDTO.error(message, errors));
	}
}
