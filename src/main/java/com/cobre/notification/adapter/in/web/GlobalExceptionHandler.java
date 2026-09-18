package com.cobre.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cobre.notification.adapter.in.web.dto.ErrorResponse;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.SubscriptionCheckException;
import com.cobre.notification.domain.exception.SubscriptionNotConfirmedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotificationDeliveryException.class)
	public ResponseEntity<ErrorResponse> handleDeliveryFailure(NotificationDeliveryException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("NOTIFICATION_DELIVERY_FAILED", ex.getMessage()));
	}

	@ExceptionHandler(SubscriptionNotConfirmedException.class)
	public ResponseEntity<ErrorResponse> handleSubscriptionNotConfirmed(SubscriptionNotConfirmedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse("SUBSCRIPTION_NOT_CONFIRMED", ex.getMessage()));
	}

	@ExceptionHandler(SubscriptionCheckException.class)
	public ResponseEntity<ErrorResponse> handleSubscriptionCheckFailure(SubscriptionCheckException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("SUBSCRIPTION_CHECK_FAILED", ex.getMessage()));
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
}
