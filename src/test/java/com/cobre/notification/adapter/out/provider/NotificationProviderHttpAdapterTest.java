package com.cobre.notification.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationRequest;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationResponse;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@ExtendWith(MockitoExtension.class)
class NotificationProviderHttpAdapterTest {

	@Mock
	private NotificationProviderHttpClient httpClient;

	private final NotificationEvent event = new NotificationEvent("EVT001", "credit_card_payment",
			"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	@Test
	void mapsTheProviderResponseIntoADeliveredResult() {
		given(httpClient.send(new ProviderNotificationRequest(event.eventId(), event.eventType(), event.content(),
				event.deliveryDate(), event.clientId())))
				.willReturn(new ProviderNotificationResponse("ref-123"));

		NotificationProviderHttpAdapter adapter = new NotificationProviderHttpAdapter(httpClient);
		DeliveryResult result = adapter.deliver(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-123"));
	}

	@Test
	void translatesAnOpenCircuitBreakerIntoADeliveryException() {
		CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
		given(httpClient.send(org.mockito.ArgumentMatchers.any()))
				.willThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

		NotificationProviderHttpAdapter adapter = new NotificationProviderHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.deliver(event))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasCauseInstanceOf(CallNotPermittedException.class);
	}

	@Test
	void translatesATransientProviderFailureIntoADeliveryException() {
		given(httpClient.send(org.mockito.ArgumentMatchers.any()))
				.willThrow(new NotificationProviderTransientException("unavailable", new RuntimeException()));

		NotificationProviderHttpAdapter adapter = new NotificationProviderHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.deliver(event))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasCauseInstanceOf(NotificationProviderTransientException.class);
	}

	@Test
	void translatesARejectionIntoADeliveryException() {
		given(httpClient.send(org.mockito.ArgumentMatchers.any()))
				.willThrow(new NotificationProviderRejectedException("bad payload", new RuntimeException()));

		NotificationProviderHttpAdapter adapter = new NotificationProviderHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.deliver(event))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasCauseInstanceOf(NotificationProviderRejectedException.class);
	}
}
