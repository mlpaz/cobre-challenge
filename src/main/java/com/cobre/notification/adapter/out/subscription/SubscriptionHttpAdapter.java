package com.cobre.notification.adapter.out.subscription;

import org.springframework.stereotype.Component;

import com.cobre.notification.domain.exception.SubscriptionCheckException;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Component
public class SubscriptionHttpAdapter implements SubscriptionPort {

	private final SubscriptionHttpClient httpClient;

	public SubscriptionHttpAdapter(SubscriptionHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public boolean isSubscribed(NotificationEvent event) {
		try {
			return httpClient.isActive(event.clientId(), event.eventType());
		} catch (SubscriptionServiceUnavailableException e) {
			throw new SubscriptionCheckException(
					"Unable to confirm subscription for event " + event.eventId(), e);
		}
	}
}
