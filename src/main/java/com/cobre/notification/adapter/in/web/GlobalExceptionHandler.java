package com.cobre.notification.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.cobre.notification.adapter.in.web.dto.ErrorResponse;
import com.cobre.notification.domain.exception.InvalidPaginationException;
import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.exception.SubscriptionNotFoundException;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.MetricsTags;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final MetricsPort metricsPort;

	public GlobalExceptionHandler(MetricsPort metricsPort) {
		this.metricsPort = metricsPort;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Invalid request");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST", message));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("MISSING_HEADER", "Missing required header: " + ex.getHeaderName()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_REQUEST",
						"Invalid value for parameter '" + ex.getName() + "': " + ex.getValue()));
	}

	@ExceptionHandler(InvalidPaginationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPagination(InvalidPaginationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("INVALID_PAGINATION", ex.getMessage()));
	}

	@ExceptionHandler(NotificationEventAccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(NotificationEventAccessDeniedException ex) {
		// Security-relevant: a caller's x-user-id didn't match the owner of the
		// requested event — worth its own trail (IDOR/enumeration probing) beyond
		// just the 400 response. See A09 in the README's Seguridad section.
		log.warn("Access denied: x-user-id={} requested notification_event_id={} it does not own",
				ex.requestedByUserId(), ex.notificationEventId());
		metricsPort.increment("notification.security.access_denied", MetricsTags.CLIENT_ID.of(ex.requestedByUserId()));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("USER_MISMATCH", ex.getMessage()));
	}

	@ExceptionHandler(NotificationEventNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NotificationEventNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOTIFICATION_EVENT_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(SubscriptionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("SUBSCRIPTION_NOT_FOUND", ex.getMessage()));
	}
}
