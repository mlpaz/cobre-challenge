package com.cobre.notification.adapter.out.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.SubscriptionCheckException;
import com.cobre.notification.domain.model.NotificationEvent;

@ExtendWith(MockitoExtension.class)
class SubscriptionHttpAdapterTest {

	@Mock
	private SubscriptionHttpClient httpClient;

	private final NotificationEvent event = new NotificationEvent("EVT001", "credit_card_payment",
			"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	@Test
	void returnsWhatTheHttpClientConfirms() {
		given(httpClient.isActive("CLIENT001", "credit_card_payment")).willReturn(true);

		SubscriptionHttpAdapter adapter = new SubscriptionHttpAdapter(httpClient);

		assertThat(adapter.isSubscribed(event)).isTrue();
	}

	@Test
	void translatesAnUnavailableRegistryIntoASubscriptionCheckException() {
		given(httpClient.isActive("CLIENT001", "credit_card_payment"))
				.willThrow(new SubscriptionServiceUnavailableException("down", new RuntimeException()));

		SubscriptionHttpAdapter adapter = new SubscriptionHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.isSubscribed(event))
				.isInstanceOf(SubscriptionCheckException.class)
				.hasCauseInstanceOf(SubscriptionServiceUnavailableException.class);
	}
}
