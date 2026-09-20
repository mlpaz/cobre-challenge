package com.cobre.notification.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cobre.notification.adapter.in.web.config.NotificationEventQueryProperties;
import com.cobre.notification.adapter.in.web.dto.NotificationEventDetailResponse;
import com.cobre.notification.adapter.in.web.dto.NotificationEventPageResponse;
import com.cobre.notification.adapter.in.web.dto.NotificationEventRequest;
import com.cobre.notification.adapter.in.web.dto.NotificationEventResponse;
import com.cobre.notification.domain.exception.InvalidPaginationException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.port.in.QueryNotificationEventsUseCase;
import com.cobre.notification.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/notification_events")
@Tag(name = "Notification events", description = "Ingesta, consulta y replay de eventos de notificación")
public class NotificationController {

	private static final String USER_ID_HEADER = "x-user-id";

	private final SendNotificationUseCase sendNotificationUseCase;
	private final QueryNotificationEventsUseCase queryNotificationEventsUseCase;
	private final ReplayNotificationEventUseCase replayNotificationEventUseCase;
	private final NotificationEventQueryProperties queryProperties;

	public NotificationController(SendNotificationUseCase sendNotificationUseCase,
			QueryNotificationEventsUseCase queryNotificationEventsUseCase,
			ReplayNotificationEventUseCase replayNotificationEventUseCase,
			NotificationEventQueryProperties queryProperties) {
		this.sendNotificationUseCase = sendNotificationUseCase;
		this.queryNotificationEventsUseCase = queryNotificationEventsUseCase;
		this.replayNotificationEventUseCase = replayNotificationEventUseCase;
		this.queryProperties = queryProperties;
	}

	@Operation(summary = "Ingesta un evento de notificación",
			description = "Mismo caso de uso que consume el listener de Kafka; pensado para pruebas manuales o integraciones que no pasan por el broker.")
	@ApiResponse(responseCode = "200", description = "Resultado del intento de entrega")
	@ApiResponse(responseCode = "400", description = "Request inválido, o event_type fuera de la lista de tipos soportados")
	@PostMapping
	public ResponseEntity<NotificationEventResponse> receive(@Valid @RequestBody NotificationEventRequest request) {
		DeliveryResult result = sendNotificationUseCase.sendNotification(NotificationEventWebMapper.toDomain(request));
		return ResponseEntity.status(HttpStatus.OK).body(NotificationEventWebMapper.toResponse(result));
	}

	@Operation(summary = "Lista los eventos del cliente autenticado",
			description = "Filtra por delivery_status y por rango de fecha del evento, paginado y ordenado por delivery_date descendente.")
	@ApiResponse(responseCode = "200", description = "Página de eventos")
	@ApiResponse(responseCode = "400", description = "Falta el header x-user-id, o limit/offset fuera de rango")
	@GetMapping
	public ResponseEntity<NotificationEventPageResponse> list(
			@Parameter(description = "Identificador del cliente dueño de los eventos", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@Parameter(description = "Filtra por estado de entrega")
			@RequestParam(name = "delivery_status", required = false) DeliveryStatus deliveryStatus,
			@Parameter(description = "Fecha mínima (inclusive) de delivery_date")
			@RequestParam(name = "created_from", required = false) Instant createdFrom,
			@Parameter(description = "Fecha máxima (inclusive) de delivery_date")
			@RequestParam(name = "created_to", required = false) Instant createdTo,
			@Parameter(description = "Cantidad de resultados por página")
			@RequestParam(name = "limit", required = false) Integer limit,
			@Parameter(description = "Cantidad de resultados a saltear")
			@RequestParam(name = "offset", required = false) Integer offset) {
		int resolvedLimit = limit == null ? queryProperties.defaultLimit() : limit;
		int resolvedOffset = offset == null ? 0 : offset;
		validatePagination(resolvedLimit, resolvedOffset);

		NotificationEventQuery query = new NotificationEventQuery(userId, deliveryStatus, createdFrom, createdTo,
				resolvedLimit, resolvedOffset);
		return ResponseEntity
				.ok(NotificationEventWebMapper.toPageResponse(queryNotificationEventsUseCase.listEvents(query)));
	}

	@Operation(summary = "Obtiene el detalle de un evento")
	@ApiResponse(responseCode = "200", description = "Evento encontrado")
	@ApiResponse(responseCode = "400", description = "El evento no pertenece al x-user-id indicado")
	@ApiResponse(responseCode = "404", description = "No existe un evento con ese id")
	@GetMapping("/{notification_event_id}")
	public ResponseEntity<NotificationEventDetailResponse> get(
			@Parameter(description = "Identificador del cliente dueño del evento", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@PathVariable("notification_event_id") UUID notificationEventId) {
		return ResponseEntity.ok(NotificationEventWebMapper
				.toDetailResponse(queryNotificationEventsUseCase.getEvent(notificationEventId, userId)));
	}

	@Operation(summary = "Reintenta la entrega de un evento",
			description = "No-op (sin volver a llamar al webhook) si el evento ya está DELIVERED.")
	@ApiResponse(responseCode = "200", description = "Resultado del reintento")
	@ApiResponse(responseCode = "400", description = "El evento no pertenece al x-user-id indicado")
	@ApiResponse(responseCode = "404", description = "No existe un evento con ese id")
	@PostMapping("/{notification_event_id}/replay")
	public ResponseEntity<NotificationEventResponse> replay(
			@Parameter(description = "Identificador del cliente dueño del evento", required = true)
			@RequestHeader(USER_ID_HEADER) String userId,
			@PathVariable("notification_event_id") UUID notificationEventId) {
		DeliveryResult result = replayNotificationEventUseCase.replay(notificationEventId, userId);
		return ResponseEntity.ok(NotificationEventWebMapper.toResponse(result));
	}

	private void validatePagination(int limit, int offset) {
		if (limit < 1 || limit > queryProperties.maxLimit()) {
			throw new InvalidPaginationException("limit must be between 1 and " + queryProperties.maxLimit());
		}
		if (offset < 0 || offset > queryProperties.maxOffset()) {
			throw new InvalidPaginationException("offset must be between 0 and " + queryProperties.maxOffset());
		}
	}
}
