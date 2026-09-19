package com.cobre.notification.domain.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	@Mock
	private SubscriptionPort subscriptionPort;

	@Test
	void delegatesSubscriptionCreationToTheOutboundPort() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		Subscription subscription = new Subscription("CLIENT001", "credit_card_payment",
				"https://client.example.com/webhooks/notifications");

		service.subscribe(subscription);

		verify(subscriptionPort).save(subscription);
	}
}
