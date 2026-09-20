package com.cobre.notification.adapter.out.webhook;

import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.out.webhook.dto.WebhookNotificationRequest;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.WebhookDeliveryPort;

@Component
public class WebhookHttpAdapter implements WebhookDeliveryPort {

	// A client's webhook can return whatever it wants in the response body —
	// cap what we persist so an unexpectedly large response can't bloat the
	// notification_events row.
	private static final int MAX_STORED_RESPONSE_LENGTH = 1000;

	private final WebhookHttpClient httpClient;

	public WebhookHttpAdapter(WebhookHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public DeliveryResult deliver(NotificationEvent event, String webHookUrl) {
		try {
			String response = httpClient.send(webHookUrl, toWebhookRequest(event));
			return new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, truncate(response));
		} catch (WebhookRejectedException | WebhookTransientException e) {
			throw new NotificationDeliveryException(
					"Delivery failed for event " + event.eventId() + ": " + e.getMessage(), e);
		}
	}

	private static WebhookNotificationRequest toWebhookRequest(NotificationEvent event) {
		return new WebhookNotificationRequest(
				event.eventId(),
				event.eventType(),
				event.content(),
				event.deliveryDate(),
				event.clientId());
	}

	private static String truncate(String response) {
		if (response == null || response.length() <= MAX_STORED_RESPONSE_LENGTH) {
			return response;
		}
		return response.substring(0, MAX_STORED_RESPONSE_LENGTH);
	}
}
