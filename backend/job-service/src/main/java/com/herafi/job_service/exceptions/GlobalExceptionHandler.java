package com.herafi.job_service.exceptions;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.method.ParameterErrors;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import com.herafi.job_service.config.JobImageProperties;
import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final JobImageProperties imageProperties;

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> new ApiErrorResponse.FieldError(
						error.getField(),
						error.getDefaultMessage()))
				.toList();

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_ERROR",
				"Request validation failed",
				request.getRequestURI(),
				fieldErrors);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_REQUEST",
				"Request body is malformed",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingHeader(
			MissingRequestHeaderException exception,
			HttpServletRequest request) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.UNAUTHORIZED.value(),
				"UNAUTHORIZED",
				"Required request header is missing",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.UNAUTHORIZED)
				.body(response);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_REQUEST",
				"Request parameter has an invalid format",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(
			Exception exception,
			HttpServletRequest request) {
		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"INTERNAL_ERROR",
				"An unexpected error occurred",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(response);
	}

	@ExceptionHandler(JobNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleJobNotFound(
			JobNotFoundException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.NOT_FOUND.value(),
				"JOB_NOT_FOUND",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(response);
	}

	@ExceptionHandler(JobAccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleJobAccessDenied(
			JobAccessDeniedException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.FORBIDDEN.value(),
				"JOB_ACCESS_DENIED",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.FORBIDDEN)
				.body(response);
	}

	@ExceptionHandler(JobStateConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleJobStateConflict(
			JobStateConflictException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				"JOB_STATE_CONFLICT",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(response);
	}

	@ExceptionHandler(InvalidJobStateException.class)
	public ResponseEntity<ApiErrorResponse> handleJobStateConflict(
			InvalidJobStateException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				"JOB_STATE_CONFLICT",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(response);
	}

	@ExceptionHandler(JobNotOpenException.class)
	public ResponseEntity<ApiErrorResponse> handleJobNotOpen(
			JobNotOpenException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				"JOB_NOT_OPEN",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(response);
	}

	@ExceptionHandler({
			ObjectOptimisticLockingFailureException.class,
			OptimisticLockException.class
	})
	public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
			Exception exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				"CONCURRENT_MODIFICATION",
				"Job changed concurrently. Reload and retry.",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(response);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
			HandlerMethodValidationException exception,
			HttpServletRequest request) {

		List<ApiErrorResponse.FieldError> fieldErrors = exception.getParameterValidationResults()
				.stream()
				.filter(ParameterErrors.class::isInstance)
				.map(ParameterErrors.class::cast)
				.flatMap(errors -> errors.getFieldErrors().stream())
				.map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
				.toList();

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				fieldErrors.isEmpty() ? "INVALID_PAGINATION" : "VALIDATION_ERROR",
				fieldErrors.isEmpty() ? "Invalid pagination parameters" : "Request validation failed",
				request.getRequestURI(),
				fieldErrors.isEmpty() ? null : fieldErrors);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(InvalidSortException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidSort(
			InvalidSortException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_SORT",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(InvalidReferenceException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidReference(
			InvalidReferenceException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_REFERENCE",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(InvalidImageException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidImage(
			InvalidImageException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_IMAGE",
				exception.getMessage(),
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingMultipartPart(
			MissingServletRequestPartException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_IMAGE",
				"Required image file part is missing",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(response);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxSizeException(
			MaxUploadSizeExceededException exception,
			HttpServletRequest request) {

		ApiErrorResponse response = new ApiErrorResponse(
				Instant.now(),
				HttpStatus.CONTENT_TOO_LARGE.value(),
				"INVALID_IMAGE",
				"File too large. Maximum upload size is " + imageProperties.maxFileSizeLabel() + ".",
				request.getRequestURI(),
				null);

		return ResponseEntity
				.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(response);
	}
}
