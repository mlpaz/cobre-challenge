package com.cobre.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cobre.notification.adapter.in.web.dto.SubscriptionRequest;
import com.cobre.notification.adapter.in.web.dto.SubscriptionResponse;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.SubscribeUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/subscriptions")
@Tag(name = "Subscriptions", description = "Webhook suscripto por cliente y tipo de evento")
public class SubscriptionController {

	private final SubscribeUseCase subscribeUseCase;

	public SubscriptionController(SubscribeUseCase subscribeUseCase) {
		this.subscribeUseCase = subscribeUseCase;
	}

	@Operation(summary = "Crea o actualiza el webhook de un cliente para un tipo de evento",
			description = "Si ya existe una suscripción para ese (user_id, event_type), actualiza la URL en vez de crear una nueva.")
	@ApiResponse(responseCode = "201", description = "Suscripción creada o actualizada")
	@ApiResponse(responseCode = "400", description = "Request inválido")
	@PostMapping
	public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request) {
		Subscription subscription = new Subscription(request.userId(), request.eventType(), request.webHookUrl());
		subscribeUseCase.subscribe(subscription);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new SubscriptionResponse(subscription.userId(), subscription.eventType(),
						subscription.webHookUrl()));
	}
}
