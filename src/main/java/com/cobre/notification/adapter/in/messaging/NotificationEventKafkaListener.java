package com.cobre.notification.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.in.messaging.dto.NotificationEventMessage;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.SubscriptionNotConfirmedException;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import tools.jackson.databind.json.JsonMapper;

/**
 * Input adapter that consumes platform-generated events from Kafka and drives
 * them into {@link SendNotificationUseCase} — the same port the REST
 * {@code NotificationController} calls. This is the production entry point;
 * the platform emits events onto this topic instead of calling us over HTTP.
 *
 * <p>Business-level outcomes (no active subscription, or delivery definitely
 * failed after the outbound adapter's own retry/circuit-breaker strategy is
 * exhausted) are terminal for this event: we log them and let the offset
 * commit, we do not ask Kafka to redeliver. Retrying an already
 * circuit-broken call at the Kafka level would just hammer the provider
 * again. Any other, unexpected exception (e.g. a malformed message) is left
 * to propagate to the listener container's error handler, which applies a
 * bounded backoff before giving up on that record.
 *
 * <p>TODO: once notification event storage exists (self-service API), persist
 * REJECTED/FAILED outcomes there instead of only logging them, so they can be
 * queried and replayed via {@code POST /notification_events/{id}/replay}.
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
		try {
			sendNotificationUseCase.sendNotification(event);
		} catch (SubscriptionNotConfirmedException e) {
			log.warn("Skipping event {}: {}", event.eventId(), e.getMessage());
		} catch (NotificationDeliveryException e) {
			log.error("Delivery definitely failed for event {}: {}", event.eventId(), e.getMessage(), e);
		}
	}
}
