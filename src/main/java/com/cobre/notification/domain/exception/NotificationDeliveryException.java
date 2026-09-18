package com.cobre.notification.domain.exception;

public class NotificationDeliveryException extends RuntimeException {

	public NotificationDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
