package com.cobre.notification.domain.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;
import com.cobre.notification.domain.port.out.IdempotencyPort;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.NotificationProviderPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

@Service
public class NotificationDeliveryService implements SendNotificationUseCase {

	private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

	private final IdempotencyPort idempotencyPort;
	private final SubscriptionPort subscriptionPort;
	private final NotificationProviderPort notificationProviderPort;
	private final NotificationRecordPort notificationRecordPort;
	private final MetricsPort metricsPort;
	private final WebhookCircuitBreakerPort webhookCircuitBreakerPort;

	public NotificationDeliveryService(IdempotencyPort idempotencyPort, SubscriptionPort subscriptionPort,
			NotificationProviderPort notificationProviderPort, NotificationRecordPort notificationRecordPort,
			MetricsPort metricsPort, WebhookCircuitBreakerPort webhookCircuitBreakerPort) {
		this.idempotencyPort = idempotencyPort;
		this.subscriptionPort = subscriptionPort;
		this.notificationProviderPort = notificationProviderPort;
		this.notificationRecordPort = notificationRecordPort;
		this.metricsPort = metricsPort;
		this.webhookCircuitBreakerPort = webhookCircuitBreakerPort;
	}

	@Override
	public DeliveryResult sendNotification(NotificationEvent event) {
		String eventTypeTag = "event_type:" + event.eventType();
		String clientIdTag = "client_id:" + event.clientId();
		log.info("Received notification event {} type={} client={}", event.eventId(), event.eventType(),
				event.clientId());
		metricsPort.increment("notification.events.received", eventTypeTag, clientIdTag);

		if (idempotencyPort.isDuplicate(event)) {
			log.info("Notification event {} is a duplicate, skipping delivery", event.eventId());
			metricsPort.increment("notification.events.duplicate", eventTypeTag, clientIdTag);
			return new DeliveryResult(event.eventId(), DeliveryStatus.DUPLICATE, null);
		}

		Optional<String> webHookUrl = subscriptionPort.findWebHookUrl(event.clientId(), event.eventType());
		if (webHookUrl.isEmpty()) {
			log.info("No webhook subscription found for client={} eventType={}", event.clientId(), event.eventType());
			metricsPort.increment("notification.subscription.webhook_not_found", eventTypeTag, clientIdTag);
			return new DeliveryResult(event.eventId(), DeliveryStatus.NOT_SUBSCRIBED, null);
		}
		String webhookTag = "webhook:" + webHookUrl.get();
		log.debug("Webhook subscription found for client={} eventType={}", event.clientId(), event.eventType());
		metricsPort.increment("notification.subscription.webhook_found", eventTypeTag, clientIdTag, webhookTag);

		DeliveryResult result;
		if (!webhookCircuitBreakerPort.isCallPermitted(event.clientId(), event.eventType())) {
			// This webhook's own score is below the configured threshold: fail fast
			// without contacting the provider, instead of hammering a webhook that's
			// already unhealthy. Still recorded, so it shows up in the query API and
			// can be retried deliberately via replay (which bypasses this check).
			log.warn("Circuit open for webhook client={} eventType={}, skipping delivery attempt", event.clientId(),
					event.eventType());
			metricsPort.increment("notification.webhook.delivery_blocked", eventTypeTag, clientIdTag, webhookTag);
			result = new DeliveryResult(event.eventId(), DeliveryStatus.CIRCUIT_OPEN, null);
		} else {
			try {
				result = notificationProviderPort.deliver(event, webHookUrl.get());
				idempotencyPort.markAsProcessed(event);
				webhookCircuitBreakerPort.recordResult(event.clientId(), event.eventType(), true);
			} catch (NotificationDeliveryException e) {
				// Delivery definitively failed (retries exhausted). We still answer
				// normally: returning an error status here could make a caller (an
				// HTTP client, or Kafka redelivering on a thrown exception) retry the
				// whole event on top of the retry strategy the outbound adapter
				// already ran. The failure is recorded instead, to be replayed
				// deliberately later via the self-service API.
				log.warn("Delivery failed for event {}: {}", event.eventId(), e.getMessage());
				result = new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null);
				webhookCircuitBreakerPort.recordResult(event.clientId(), event.eventType(), false);
			}
		}
		notificationRecordPort.save(event, result);
		log.info("Notification event {} saved with status={}", event.eventId(), result.status());
		metricsPort.increment("notification.events.saved", "delivery_status:" + result.status(), clientIdTag);
		return result;
	}
}
