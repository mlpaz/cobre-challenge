package com.cobre.notification.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.cobre.notification.adapter.out.provider.config.NotificationProviderProperties;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationRequest;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationResponse;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

/**
 * Exercises the real retry behavior of the Notification Provider HTTP client
 * against a mocked HTTP server.
 */
class NotificationProviderHttpClientTest {

	private static final String URL = "http://notification-provider.local/notifications";

	private final ProviderNotificationRequest request = new ProviderNotificationRequest("EVT001",
			"credit_card_payment", "Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001",
			"https://client.example.com/webhooks/notifications");

	private final MetricsPort metricsPort = Mockito.mock(MetricsPort.class);
	private final WebhookCircuitBreakerPort webhookCircuitBreakerPort = Mockito.mock(WebhookCircuitBreakerPort.class);

	@Test
	void succeedsOnTheFirstAttempt() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"reference\":\"ref-1\"}", MediaType.APPLICATION_JSON));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10)), metricsPort, webhookCircuitBreakerPort);

		ProviderNotificationResponse response = client.send(request);

		assertThat(response).isEqualTo(new ProviderNotificationResponse("ref-1"));
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:200",
				"event_type:credit_card_payment", "client_id:CLIENT001", "webhook:" + request.webHookUrl());
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", true);
	}

	@Test
	void doesNotRetryOnAClientErrorButStillRecordsItAsAFailure() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10)), metricsPort, webhookCircuitBreakerPort);

		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderRejectedException.class);
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:400",
				"event_type:credit_card_payment", "client_id:CLIENT001", "webhook:" + request.webHookUrl());
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", false);
	}

	@Test
	void everyAttemptCountsTowardsTheScoreIncludingTheOnesRetriedInternally() {
		// This is the whole point of recording per HTTP attempt instead of once
		// per send() call: a delivery that fails once and then succeeds on retry
		// must show up as one failure AND one success against the webhook's
		// score, not get collapsed into a single success.
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"reference\":\"ref-2\"}", MediaType.APPLICATION_JSON));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10)), metricsPort, webhookCircuitBreakerPort);

		ProviderNotificationResponse response = client.send(request);

		assertThat(response).isEqualTo(new ProviderNotificationResponse("ref-2"));
		server.verify();
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", false);
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", true);
	}

	@Test
	void exhaustsRetriesAndFailsWithATransientExceptionRecordingEveryAttempt() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (int i = 0; i < 3; i++) {
			server.expect(requestTo(URL)).andRespond(withServerError());
		}

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10)), metricsPort, webhookCircuitBreakerPort);

		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderTransientException.class);
		server.verify();
		verify(webhookCircuitBreakerPort, times(3)).recordResult("CLIENT001", "credit_card_payment", false);
	}

	private static NotificationProviderProperties properties(int maxAttempts, Duration waitDuration) {
		return new NotificationProviderProperties(
				"http://notification-provider.local",
				"/notifications",
				Duration.ofSeconds(2),
				Duration.ofSeconds(3),
				new NotificationProviderProperties.Retry(maxAttempts, waitDuration, 1.0));
	}
}
