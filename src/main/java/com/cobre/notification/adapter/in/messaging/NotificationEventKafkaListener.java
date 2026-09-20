package com.cobre.notification.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.in.messaging.dto.NotificationEventMessage;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import tools.jackson.databind.json.JsonMapper;

/**
 * Input adapter that consumes platform-generated events from Kafka and drives
 * them into {@link SendNotificationUseCase} — the same port the REST
 * {@code NotificationController} calls. This is the production entry point;
 * the platform emits events onto this topic instead of calling us over HTTP.
 *
 * <p>{@link SendNotificationUseCase#sendNotification} never throws for a
 * business/delivery outcome (no subscription, that webhook's circuit breaker
 * is open, or delivery definitely failed after the outbound adapter's own
 * retry strategy is exhausted): it always returns a result and records it.
 * We deliberately do not fail this listener on a bad outcome — throwing here
 * would make Kafka redeliver the record and hammer the webhook again on top
 * of the retry strategy that already ran. Only a genuinely unexpected
 * exception (e.g. a malformed message) propagates to the listener
 * container's error handler, which applies a bounded backoff before giving up
 * on that record.
 */
@Component
public class NotificationEventKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationEventKafkaListener.class);

	private final SendNotificationUseCase sendNotificationUseCase;
	private final JsonMapper jsonMapper;

	public NotificationEventKafkaListener(SendNotificationUseCase sendNotificationUseCase, JsonMapper jsonMapper) {
		this.sendNotificationUseCase = sendNotificationUseCase;
		this.jsonMapper = jsonMapper;
	}

	@KafkaListener(topics = "${notification.events.topic}", groupId = "${spring.kafka.consumer.group-id}")
	public void onMessage(ConsumerRecord<String, String> record) {
		NotificationEventMessage message = jsonMapper.readValue(record.value(), NotificationEventMessage.class);
		NotificationEvent event = NotificationEventMessageMapper.toDomain(message);
		DeliveryResult result = sendNotificationUseCase.sendNotification(event);
		if (result.status() == DeliveryStatus.FAILED) {
			log.error("Delivery definitely failed for event {}", event.eventId());
		} else if (result.status() == DeliveryStatus.CIRCUIT_OPEN) {
			log.warn("Delivery skipped for event {}: webhook circuit breaker is open", event.eventId());
		}
	}
}
