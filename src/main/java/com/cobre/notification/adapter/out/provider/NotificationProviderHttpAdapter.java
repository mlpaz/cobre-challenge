package com.cobre.notification.adapter.out.provider;

import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationRequest;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationResponse;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.NotificationProviderPort;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@Component
public class NotificationProviderHttpAdapter implements NotificationProviderPort {

	private final NotificationProviderHttpClient httpClient;

	public NotificationProviderHttpAdapter(NotificationProviderHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public DeliveryResult deliver(NotificationEvent event) {
		try {
			ProviderNotificationResponse response = httpClient.send(toProviderRequest(event));
			return new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, response.reference());
		} catch (CallNotPermittedException e) {
			throw new NotificationDeliveryException(
					"Notification provider circuit breaker is open, delivery for event "
							+ event.eventId() + " was short-circuited",
					e);
		} catch (NotificationProviderRejectedException | NotificationProviderTransientException e) {
			throw new NotificationDeliveryException(
					"Delivery failed for event " + event.eventId() + ": " + e.getMessage(), e);
		}
	}

	private static ProviderNotificationRequest toProviderRequest(NotificationEvent event) {
		return new ProviderNotificationRequest(
				event.eventId(),
				event.eventType(),
				event.content(),
				event.deliveryDate(),
				event.clientId());
	}
}
