package com.cobre.notification.domain.exception;

public class InvalidEventTypeException extends RuntimeException {

	public InvalidEventTypeException(String eventType) {
		super("Unknown event_type: " + eventType);
	}
}
