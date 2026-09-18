package com.cobre.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cobre.notification.adapter.in.web.dto.NotificationEventRequest;
import com.cobre.notification.adapter.in.web.dto.NotificationEventResponse;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/notification_events")
public class NotificationController {

	private final SendNotificationUseCase sendNotificationUseCase;

	public NotificationController(SendNotificationUseCase sendNotificationUseCase) {
		this.sendNotificationUseCase = sendNotificationUseCase;
	}

	@PostMapping
	public ResponseEntity<NotificationEventResponse> receive(@Valid @RequestBody NotificationEventRequest request) {
		DeliveryResult result = sendNotificationUseCase.sendNotification(NotificationEventWebMapper.toDomain(request));
		return ResponseEntity.status(HttpStatus.OK).body(NotificationEventWebMapper.toResponse(result));
	}
}
