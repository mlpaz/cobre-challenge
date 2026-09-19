package com.cobre.notification.adapter.out.provider;

import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationRequest;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationResponse;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.NotificationProviderPort;

@Component
public class NotificationProviderHttpAdapter implements NotificationProviderPort {

	private final NotificationProviderHttpClient httpClient;

	public NotificationProviderHttpAdapter(NotificationProviderHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public DeliveryResult deliver(NotificationEvent event, String webHookUrl) {
		try {
			ProviderNotificationResponse response = httpClient.send(toProviderRequest(event, webHookUrl));
			return new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, response.reference());
		} catch (NotificationProviderRejectedException | NotificationProviderTransientException e) {
			throw new NotificationDeliveryException(
					"Delivery failed for event " + event.eventId() + ": " + e.getMessage(), e);
		}
	}

	private static ProviderNotificationRequest toProviderRequest(NotificationEvent event, String webHookUrl) {
		return new ProviderNotificationRequest(
				event.eventId(),
				event.eventType(),
				event.content(),
				event.deliveryDate(),
				event.clientId(),
				webHookUrl);
	}
}
