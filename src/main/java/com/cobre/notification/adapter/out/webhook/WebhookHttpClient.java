package com.cobre.notification.adapter.out.webhook;

import java.net.URI;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.cobre.notification.adapter.out.webhook.config.WebhookHttpProperties;
import com.cobre.notification.adapter.out.webhook.dto.WebhookNotificationRequest;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.MetricsTags;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Technical HTTP client that calls a client's webhook URL directly. Owns the
 * retry strategy for this outbound dependency, fully driven by
 * {@link WebhookHttpProperties}. Wired as a bean by
 * {@link WebhookHttpClientConfig} rather than component-scanned, since
 * construction needs to combine independently built collaborators
 * (RestClient, Retry) from configuration properties.
 *
 * <p>Deliberately has no circuit breaker at this level: that protection is
 * per webhook — see {@link WebhookCircuitBreakerPort} — and is fed from
 * here, once per actual HTTP attempt (including every internal retry),
 * rather than once per {@link #send} call: a delivery that fails twice
 * before succeeding on retry still counts as two failures and a success
 * against that webhook's score, not a single success.
 */
public class WebhookHttpClient {

	private static final Logger log = LoggerFactory.getLogger(WebhookHttpClient.class);

	private final RestClient restClient;
	private final Retry retry;
	private final MetricsPort metricsPort;
	private final WebhookCircuitBreakerPort webhookCircuitBreakerPort;

	public WebhookHttpClient(WebhookHttpProperties properties, MetricsPort metricsPort,
			WebhookCircuitBreakerPort webhookCircuitBreakerPort) {
		this(buildRestClient(properties), properties, metricsPort, webhookCircuitBreakerPort);
	}

	/**
	 * Visible for tests: allows binding a {@link RestClient} to a mock HTTP
	 * server while still exercising the real retry behavior.
	 */
	WebhookHttpClient(RestClient restClient, WebhookHttpProperties properties, MetricsPort metricsPort,
			WebhookCircuitBreakerPort webhookCircuitBreakerPort) {
		this.restClient = restClient;
		this.retry = buildRetry(properties.retry());
		this.metricsPort = metricsPort;
		this.webhookCircuitBreakerPort = webhookCircuitBreakerPort;
	}

	public String send(String webHookUrl, WebhookNotificationRequest request) {
		Supplier<String> call = () -> doPost(webHookUrl, request);
		return Retry.decorateSupplier(retry, call).get();
	}

	private String doPost(String webHookUrl, WebhookNotificationRequest request) {
		try {
			ResponseEntity<String> response = restClient.post()
					.uri(URI.create(webHookUrl))
					.body(request)
					.retrieve()
					.toEntity(String.class);
			recordWebhookResponse(request, webHookUrl, String.valueOf(response.getStatusCode().value()));
			recordCircuitBreakerResult(request, true);
			log.debug("Webhook responded {} for event {}", response.getStatusCode().value(), request.eventId());
			return response.getBody();
		} catch (HttpClientErrorException e) {
			recordWebhookResponse(request, webHookUrl, String.valueOf(e.getStatusCode().value()));
			recordCircuitBreakerResult(request, false);
			log.warn("Webhook rejected event {}: {}", request.eventId(), e.getStatusCode());
			throw new WebhookRejectedException(
					"Webhook rejected event " + request.eventId() + ": " + e.getStatusCode(), e);
		} catch (HttpServerErrorException e) {
			recordWebhookResponse(request, webHookUrl, String.valueOf(e.getStatusCode().value()));
			recordCircuitBreakerResult(request, false);
			log.warn("Webhook unavailable (status {}) for event {}", e.getStatusCode(), request.eventId());
			throw transientException(request, e);
		} catch (ResourceAccessException e) {
			recordWebhookResponse(request, webHookUrl, "timeout");
			recordCircuitBreakerResult(request, false);
			log.warn("Webhook unreachable for event {}: {}", request.eventId(), e.getMessage());
			throw transientException(request, e);
		}
	}

	private void recordWebhookResponse(WebhookNotificationRequest request, String webHookUrl, String statusCode) {
		metricsPort.increment("notification.webhook.response", MetricsTags.STATUS_CODE.of(statusCode),
				MetricsTags.EVENT_TYPE.of(request.eventType()), MetricsTags.CLIENT_ID.of(request.clientId()),
				MetricsTags.WEBHOOK.of(webHookUrl));
	}

	private void recordCircuitBreakerResult(WebhookNotificationRequest request, boolean success) {
		webhookCircuitBreakerPort.recordResult(request.clientId(), request.eventType(), success);
	}

	private static WebhookTransientException transientException(WebhookNotificationRequest request, Exception cause) {
		return new WebhookTransientException("Webhook unavailable for event " + request.eventId(), cause);
	}

	private static RestClient buildRestClient(WebhookHttpProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
		requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	private static Retry buildRetry(WebhookHttpProperties.Retry properties) {
		IntervalFunction intervalFunction = properties.exponentialBackoffMultiplier() > 1.0
				? IntervalFunction.ofExponentialBackoff(properties.waitDuration(), properties.exponentialBackoffMultiplier())
				: IntervalFunction.of(properties.waitDuration());
		RetryConfig retryConfig = RetryConfig.custom()
				.maxAttempts(properties.maxAttempts())
				.intervalFunction(intervalFunction)
				// Only transient (5xx/timeout/connection) failures are retried; rejections
				// (4xx) fail fast instead.
				.retryOnException(ex -> ex instanceof WebhookTransientException)
				.build();
		return Retry.of("webhookDelivery", retryConfig);
	}
}
