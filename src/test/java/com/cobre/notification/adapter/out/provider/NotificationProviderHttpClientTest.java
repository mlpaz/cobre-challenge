package com.cobre.notification.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Exercises the real retry and circuit-breaker behavior of the Notification
 * Provider HTTP client against a mocked HTTP server.
 */
class NotificationProviderHttpClientTest {

	private static final String URL = "http://notification-provider.local/notifications";

	private final ProviderNotificationRequest request = new ProviderNotificationRequest("EVT001",
			"credit_card_payment", "Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001",
			"https://client.example.com/webhooks/notifications");

	private final MetricsPort metricsPort = Mockito.mock(MetricsPort.class);

	@Test
	void succeedsOnTheFirstAttempt() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"reference\":\"ref-1\"}", MediaType.APPLICATION_JSON));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10), 50, 10, 100), metricsPort);

		ProviderNotificationResponse response = client.send(request);

		assertThat(response).isEqualTo(new ProviderNotificationResponse("ref-1"));
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:200",
				"event_type:credit_card_payment");
	}

	@Test
	void doesNotRetryOnAClientError() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10), 50, 10, 100), metricsPort);

		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderRejectedException.class);
		server.verify();
		verify(metricsPort).increment("notification.webhook.response", "status_code:400",
				"event_type:credit_card_payment");
	}

	@Test
	void retriesTransientFailuresUntilItSucceeds() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"reference\":\"ref-2\"}", MediaType.APPLICATION_JSON));

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10), 50, 10, 100), metricsPort);

		ProviderNotificationResponse response = client.send(request);

		assertThat(response).isEqualTo(new ProviderNotificationResponse("ref-2"));
		server.verify();
	}

	@Test
	void exhaustsRetriesAndFailsWithATransientException() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (int i = 0; i < 3; i++) {
			server.expect(requestTo(URL)).andRespond(withServerError());
		}

		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(3, Duration.ofMillis(10), 50, 10, 100), metricsPort);

		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderTransientException.class);
		server.verify();
	}

	@Test
	void opensTheCircuitAfterEnoughFailuresAndShortCircuitsFurtherCalls() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://notification-provider.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withServerError());

		// maxAttempts=1 (no retries) so each logical call maps to exactly one recorded
		// circuit breaker outcome; a sliding window of 2 with a 50% threshold opens the
		// circuit right after those two failures.
		NotificationProviderHttpClient client = new NotificationProviderHttpClient(builder.build(),
				properties(1, Duration.ofMillis(10), 50, 2, 2), metricsPort);

		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderTransientException.class);
		assertThatThrownBy(() -> client.send(request)).isInstanceOf(NotificationProviderTransientException.class);

		// Circuit is now open: this call must fail fast, without another HTTP request.
		assertThatThrownBy(() -> client.send(request)).isInstanceOf(CallNotPermittedException.class);

		server.verify();
	}

	private static NotificationProviderProperties properties(int maxAttempts, Duration waitDuration,
			float failureRateThreshold, int slidingWindowSize, int minimumNumberOfCalls) {
		return new NotificationProviderProperties(
				"http://notification-provider.local",
				"/notifications",
				Duration.ofSeconds(2),
				Duration.ofSeconds(3),
				new NotificationProviderProperties.Retry(maxAttempts, waitDuration, 1.0),
				new NotificationProviderProperties.CircuitBreaker(failureRateThreshold, slidingWindowSize,
						minimumNumberOfCalls, Duration.ofMinutes(1), 1));
	}
}
