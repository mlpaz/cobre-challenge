package com.cobre.notification.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cobre.notification.adapter.in.web.dto.SubscriptionRequest;
import com.cobre.notification.adapter.in.web.dto.SubscriptionResponse;
import com.cobre.notification.adapter.in.web.dto.SubscriptionStatusResponse;
import com.cobre.notification.adapter.in.web.dto.SubscriptionUpdateRequest;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.DeleteSubscriptionUseCase;
import com.cobre.notification.domain.port.in.QuerySubscriptionsUseCase;
import com.cobre.notification.domain.port.in.SubscribeUseCase;
import com.cobre.notification.domain.port.in.UpdateSubscriptionUseCase;

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
	private final UpdateSubscriptionUseCase updateSubscriptionUseCase;
	private final QuerySubscriptionsUseCase querySubscriptionsUseCase;
	private final DeleteSubscriptionUseCase deleteSubscriptionUseCase;

	public SubscriptionController(SubscribeUseCase subscribeUseCase,
			UpdateSubscriptionUseCase updateSubscriptionUseCase,
			QuerySubscriptionsUseCase querySubscriptionsUseCase,
			DeleteSubscriptionUseCase deleteSubscriptionUseCase) {
		this.subscribeUseCase = subscribeUseCase;
		this.updateSubscriptionUseCase = updateSubscriptionUseCase;
		this.querySubscriptionsUseCase = querySubscriptionsUseCase;
		this.deleteSubscriptionUseCase = deleteSubscriptionUseCase;
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

	@Operation(summary = "Lista las suscripciones del cliente autenticado",
			description = "Devuelve una fila por event_type suscripto para el x-user-id del caller, con el score "
					+ "y el estado del circuit breaker de cada webhook.")
	@ApiResponse(responseCode = "200", description = "Suscripciones del cliente (puede ser una lista vacía)")
	@ApiResponse(responseCode = "400", description = "Falta el header x-user-id")
	@GetMapping
	public ResponseEntity<List<SubscriptionStatusResponse>> list(
			@Parameter(description = "Identificador del cliente dueño de las suscripciones", required = true)
			@RequestHeader(USER_ID_HEADER) String userId) {
		List<SubscriptionStatusResponse> subscriptions = querySubscriptionsUseCase.listByUserId(userId).stream()
				.map(status -> new SubscriptionStatusResponse(status.userId(), status.eventType(),
						status.webHookUrl(), status.successScore(), status.open()))
				.toList();
		return ResponseEntity.ok(subscriptions);
	}

	@Operation(summary = "Actualiza el webhook de una suscripción existente",
			description = "A diferencia de POST /subscriptions, no crea una suscripción nueva: falla con 404 si el cliente autenticado no tiene una suscripción para ese event_type.")
	@ApiResponse(responseCode = "200", description = "Suscripción actualizada")
	@ApiResponse(responseCode = "400", description = "Request inválido, o falta el header x-user-id")
	@ApiResponse(responseCode = "404", description = "No existe una suscripción de ese cliente para ese event_type")
	@PutMapping("/{event_type}")
	public ResponseEntity<SubscriptionResponse> update(
			@Parameter(description = "Identificador del cliente dueño de la suscripción", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@PathVariable("event_type") String eventType,
			@Valid @RequestBody SubscriptionUpdateRequest request) {
		Subscription subscription = new Subscription(userId, eventType, request.webHookUrl());
		updateSubscriptionUseCase.update(subscription);
		return ResponseEntity.ok(new SubscriptionResponse(userId, eventType, request.webHookUrl()));
	}

	@Operation(summary = "Elimina la suscripción del cliente autenticado para un tipo de evento",
			description = "Falla con 404 si el cliente autenticado no tiene una suscripción para ese event_type.")
	@ApiResponse(responseCode = "204", description = "Suscripción eliminada")
	@ApiResponse(responseCode = "400", description = "Falta el header x-user-id")
	@ApiResponse(responseCode = "404", description = "No existe una suscripción de ese cliente para ese event_type")
	@DeleteMapping("/{event_type}")
	public ResponseEntity<Void> delete(
			@Parameter(description = "Identificador del cliente dueño de la suscripción", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@PathVariable("event_type") String eventType) {
		deleteSubscriptionUseCase.delete(userId, eventType);
		return ResponseEntity.noContent().build();
	}
}
