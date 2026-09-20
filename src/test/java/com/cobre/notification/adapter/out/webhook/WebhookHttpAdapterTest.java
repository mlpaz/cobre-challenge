package com.cobre.notification.adapter.out.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.adapter.out.webhook.dto.WebhookNotificationRequest;
import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

@ExtendWith(MockitoExtension.class)
class WebhookHttpAdapterTest {

	private static final String WEBHOOK_URL = "https://client.example.com/webhooks/notifications";

	@Mock
	private WebhookHttpClient httpClient;

	private final NotificationEvent event = new NotificationEvent("EVT001", "credit_card_payment",
			"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	@Test
	void mapsTheWebhookResponseIntoADeliveredResult() {
		given(httpClient.send(WEBHOOK_URL, new WebhookNotificationRequest(event.eventId(), event.eventType(),
				event.content(), event.deliveryDate(), event.clientId())))
				.willReturn("{\"received\":true}");

		WebhookHttpAdapter adapter = new WebhookHttpAdapter(httpClient);
		DeliveryResult result = adapter.deliver(event, WEBHOOK_URL);

		assertThat(result)
				.isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "{\"received\":true}"));
	}

	@Test
	void truncatesAnUnexpectedlyLargeResponseBeforeStoringIt() {
		String hugeResponse = "x".repeat(2000);
		given(httpClient.send(eq(WEBHOOK_URL), any())).willReturn(hugeResponse);

		WebhookHttpAdapter adapter = new WebhookHttpAdapter(httpClient);
		DeliveryResult result = adapter.deliver(event, WEBHOOK_URL);

		assertThat(result.webhookResponse()).hasSize(1000);
	}

	@Test
	void translatesATransientWebhookFailureIntoADeliveryException() {
		given(httpClient.send(eq(WEBHOOK_URL), any()))
				.willThrow(new WebhookTransientException("unavailable", new RuntimeException()));

		WebhookHttpAdapter adapter = new WebhookHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.deliver(event, WEBHOOK_URL))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasCauseInstanceOf(WebhookTransientException.class);
	}

	@Test
	void translatesARejectionIntoADeliveryException() {
		given(httpClient.send(eq(WEBHOOK_URL), any()))
				.willThrow(new WebhookRejectedException("bad payload", new RuntimeException()));

		WebhookHttpAdapter adapter = new WebhookHttpAdapter(httpClient);

		assertThatThrownBy(() -> adapter.deliver(event, WEBHOOK_URL))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasCauseInstanceOf(WebhookRejectedException.class);
	}
}
