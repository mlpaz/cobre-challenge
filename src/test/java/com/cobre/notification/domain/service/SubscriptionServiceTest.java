package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.SubscriptionNotFoundException;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.SubscriptionStatus;
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

	@Test
	void updatesAnExistingSubscription() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		Subscription subscription = new Subscription("CLIENT001", "credit_card_payment",
				"https://client.example.com/webhooks/new");
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment"))
				.willReturn(Optional.of("https://client.example.com/webhooks/old"));

		service.update(subscription);

		verify(subscriptionPort).save(subscription);
	}

	@Test
	void throwsWhenUpdatingASubscriptionThatDoesNotExist() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		Subscription subscription = new Subscription("CLIENT001", "credit_card_payment",
				"https://client.example.com/webhooks/new");
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(subscription))
				.isInstanceOf(SubscriptionNotFoundException.class);
		verify(subscriptionPort, never()).save(any());
	}

	@Test
	void listsAllSubscriptionsForAUser() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		List<SubscriptionStatus> subscriptions = List.of(
				new SubscriptionStatus("CLIENT001", "credit_card_payment", "https://client.example.com/webhooks/a",
						100, false),
				new SubscriptionStatus("CLIENT001", "debit_card_withdrawal", "https://client.example.com/webhooks/b",
						26, true));
		given(subscriptionPort.findByUserId("CLIENT001")).willReturn(subscriptions);

		assertThat(service.listByUserId("CLIENT001")).isEqualTo(subscriptions);
	}

	@Test
	void deletesAnExistingSubscription() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment"))
				.willReturn(Optional.of("https://client.example.com/webhooks/a"));

		service.delete("CLIENT001", "credit_card_payment");

		verify(subscriptionPort).delete("CLIENT001", "credit_card_payment");
	}

	@Test
	void throwsWhenDeletingASubscriptionThatDoesNotExist() {
		SubscriptionService service = new SubscriptionService(subscriptionPort);
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete("CLIENT001", "credit_card_payment"))
				.isInstanceOf(SubscriptionNotFoundException.class);
		verify(subscriptionPort, never()).delete(any(), any());
	}
}
