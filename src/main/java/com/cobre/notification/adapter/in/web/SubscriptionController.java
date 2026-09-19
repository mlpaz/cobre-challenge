package com.cobre.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cobre.notification.adapter.in.web.dto.SubscriptionRequest;
import com.cobre.notification.adapter.in.web.dto.SubscriptionResponse;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.SubscribeUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/subscriptions")
@Tag(name = "Subscriptions", description = "Webhook suscripto por cliente y tipo de evento")
public class SubscriptionController {

	private static final String USER_ID_HEADER = "x-user-id";

	private final SubscribeUseCase subscribeUseCase;

	public SubscriptionController(SubscribeUseCase subscribeUseCase) {
		this.subscribeUseCase = subscribeUseCase;
	}

	@Operation(summary = "Crea o actualiza el webhook del cliente autenticado para un tipo de evento",
			description = "Si ya existe una suscripción para ese (x-user-id, event_type), actualiza la URL en vez de crear una nueva. El dueño de la suscripción es siempre el x-user-id del caller, nunca un valor del body.")
	@ApiResponse(responseCode = "201", description = "Suscripción creada o actualizada")
	@ApiResponse(responseCode = "400", description = "Request inválido, o falta el header x-user-id")
	@PostMapping
	public ResponseEntity<SubscriptionResponse> subscribe(
			@Parameter(description = "Identificador del cliente dueño de la suscripción", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@Valid @RequestBody SubscriptionRequest request) {
		Subscription subscription = new Subscription(userId, request.eventType(), request.webHookUrl());
		subscribeUseCase.subscribe(subscription);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new SubscriptionResponse(subscription.userId(), subscription.eventType(),
						subscription.webHookUrl()));
	}
}
