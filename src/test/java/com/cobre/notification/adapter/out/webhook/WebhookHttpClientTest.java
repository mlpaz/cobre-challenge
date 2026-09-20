package com.cobre.notification.adapter.out.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.cobre.notification.adapter.out.webhook.config.WebhookHttpProperties;
import com.cobre.notification.adapter.out.webhook.dto.WebhookNotificationRequest;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

/**
 * Exercises the real retry behavior of the webhook HTTP client against a
 * mocked HTTP server, hitting the webhook URL directly (no fixed base URL).
 */
class WebhookHttpClientTest {

	private static final String WEBHOOK_URL = "https://client.example.com/webhooks/notifications";

	private final WebhookNotificationRequest request = new WebhookNotificationRequest("EVT001",
			"credit_card_payment", "Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	private final MetricsPort metricsPort = Mockito.mock(MetricsPort.class);
	private final WebhookCircuitBreakerPort webhookCircuitBreakerPort = Mockito.mock(WebhookCircuitBreakerPort.class);

	@Test
	void succeedsOnTheFirstAttempt() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(WEBHOOK_URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"received\":true}", MediaType.APPLICATION_JSON));

		WebhookHttpClient client = new WebhookHttpClient(builder.build(), properties(3, Duration.ofMillis(10)),
				metricsPort, webhookCircuitBreakerPort);

		String response = client.send(WEBHOOK_URL, request);

		assertThat(response).isEqualTo("{\"received\":true}");
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:200",
				"event_type:credit_card_payment", "client_id:CLIENT001", "webhook:" + WEBHOOK_URL);
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", true);
	}

	@Test
	void doesNotRetryOnAClientErrorButStillRecordsItAsAFailure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(WEBHOOK_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		WebhookHttpClient client = new WebhookHttpClient(builder.build(), properties(3, Duration.ofMillis(10)),
				metricsPort, webhookCircuitBreakerPort);

		assertThatThrownBy(() -> client.send(WEBHOOK_URL, request)).isInstanceOf(WebhookRejectedException.class);
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:400",
				"event_type:credit_card_payment", "client_id:CLIENT001", "webhook:" + WEBHOOK_URL);
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", false);
	}

	@Test
	void treatsARedirectAsARejectionInsteadOfASuccessfulDelivery() {
		// RestClient's default error handler only throws for 4xx/5xx -- a 3xx
		// would otherwise sail through toEntity() and get recorded as DELIVERED
		// even though the webhook never actually accepted the event.
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(WEBHOOK_URL))
				.andRespond(withStatus(HttpStatus.FOUND).location(URI.create("https://other-host.example.com/")));

		WebhookHttpClient client = new WebhookHttpClient(builder.build(), properties(3, Duration.ofMillis(10)),
				metricsPort, webhookCircuitBreakerPort);

		assertThatThrownBy(() -> client.send(WEBHOOK_URL, request)).isInstanceOf(WebhookRejectedException.class);
		server.verify();
		// Not retried, same as a 4xx: this webhook's own response, not a
		// transient network blip -- calling it again would just get the same
		// redirect back.
		verify(metricsPort).increment("notification.webhook.response", "status_code:302",
				"event_type:credit_card_payment", "client_id:CLIENT001", "webhook:" + WEBHOOK_URL);
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", false);
	}

	@Test
	void everyAttemptCountsTowardsTheScoreIncludingTheOnesRetriedInternally() {
		// This is the whole point of recording per HTTP attempt instead of once
		// per send() call: a delivery that fails once and then succeeds on retry
		// must show up as one failure AND one success against the webhook's
		// score, not get collapsed into a single success.
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());
		server.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

		WebhookHttpClient client = new WebhookHttpClient(builder.build(), properties(3, Duration.ofMillis(10)),
				metricsPort, webhookCircuitBreakerPort);

		String response = client.send(WEBHOOK_URL, request);

		assertThat(response).isEqualTo("ok");
		server.verify();
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", false);
		verify(webhookCircuitBreakerPort).recordResult("CLIENT001", "credit_card_payment", true);
	}

	@Test
	void exhaustsRetriesAndFailsWithATransientExceptionRecordingEveryAttempt() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (int i = 0; i < 3; i++) {
			server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());
		}

		WebhookHttpClient client = new WebhookHttpClient(builder.build(), properties(3, Duration.ofMillis(10)),
				metricsPort, webhookCircuitBreakerPort);

		assertThatThrownBy(() -> client.send(WEBHOOK_URL, request)).isInstanceOf(WebhookTransientException.class);
		server.verify();
		verify(webhookCircuitBreakerPort, times(3)).recordResult("CLIENT001", "credit_card_payment", false);
	}

	private static WebhookHttpProperties properties(int maxAttempts, Duration waitDuration) {
		return new WebhookHttpProperties(
				Duration.ofSeconds(2),
				Duration.ofSeconds(3),
				new WebhookHttpProperties.Retry(maxAttempts, waitDuration, 1.0));
	}
}
