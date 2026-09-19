package com.cobre.notification.domain.exception;

public class InvalidPaginationException extends RuntimeException {

	public InvalidPaginationException(String message) {
		super(message);
	}
}
