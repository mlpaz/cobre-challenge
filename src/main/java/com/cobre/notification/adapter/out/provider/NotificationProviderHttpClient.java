package com.cobre.notification.adapter.out.provider;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.cobre.notification.adapter.out.provider.config.NotificationProviderProperties;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationRequest;
import com.cobre.notification.adapter.out.provider.dto.ProviderNotificationResponse;
import com.cobre.notification.domain.port.out.MetricsPort;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Technical HTTP client for the Notification Provider. Owns the resilience
 * strategy (retry + circuit breaker) for this specific outbound dependency,
 * fully driven by {@link NotificationProviderProperties}. Wired as a bean by
 * {@link NotificationProviderClientConfig} rather than component-scanned,
 * since construction needs to combine three independently built collaborators
 * (RestClient, Retry, CircuitBreaker) from configuration properties.
 */
public class NotificationProviderHttpClient {

	private static final Logger log = LoggerFactory.getLogger(NotificationProviderHttpClient.class);

	private final RestClient restClient;
	private final String path;
	private final Retry retry;
	private final CircuitBreaker circuitBreaker;
	private final MetricsPort metricsPort;

	public NotificationProviderHttpClient(NotificationProviderProperties properties, MetricsPort metricsPort) {
		this(buildRestClient(properties), properties, metricsPort);
	}

	/**
	 * Visible for tests: allows binding a {@link RestClient} to a mock HTTP
	 * server while still exercising the real retry/circuit-breaker behavior.
	 */
	NotificationProviderHttpClient(RestClient restClient, NotificationProviderProperties properties,
			MetricsPort metricsPort) {
		this.path = properties.path();
		this.restClient = restClient;
		this.retry = buildRetry(properties.retry());
		this.circuitBreaker = buildCircuitBreaker(properties.circuitBreaker());
		this.metricsPort = metricsPort;
	}

	public ProviderNotificationResponse send(ProviderNotificationRequest request) {
		Supplier<ProviderNotificationResponse> call = () -> doPost(request);
		Supplier<ProviderNotificationResponse> resilientCall = Retry.decorateSupplier(retry,
				CircuitBreaker.decorateSupplier(circuitBreaker, call));
		return resilientCall.get();
	}

	private ProviderNotificationResponse doPost(ProviderNotificationRequest request) {
		try {
			ResponseEntity<ProviderNotificationResponse> response = restClient.post()
					.uri(path)
					.body(request)
					.retrieve()
					.toEntity(ProviderNotificationResponse.class);
			recordWebhookResponse(request, String.valueOf(response.getStatusCode().value()));
			log.debug("Notification provider responded {} for event {}", response.getStatusCode().value(),
					request.eventId());
			return response.getBody();
		} catch (HttpClientErrorException e) {
			recordWebhookResponse(request, String.valueOf(e.getStatusCode().value()));
			log.warn("Notification provider rejected event {}: {}", request.eventId(), e.getStatusCode());
			throw new NotificationProviderRejectedException(
					"Notification provider rejected event " + request.eventId() + ": " + e.getStatusCode(), e);
		} catch (HttpServerErrorException e) {
			recordWebhookResponse(request, String.valueOf(e.getStatusCode().value()));
			log.warn("Notification provider unavailable (status {}) for event {}", e.getStatusCode(),
					request.eventId());
			throw transientException(request, e);
		} catch (ResourceAccessException e) {
			recordWebhookResponse(request, "timeout");
			log.warn("Notification provider unreachable for event {}: {}", request.eventId(), e.getMessage());
			throw transientException(request, e);
		}
	}

	private void recordWebhookResponse(ProviderNotificationRequest request, String statusCode) {
		metricsPort.increment("notification.webhook.response", "status_code:" + statusCode,
				"event_type:" + request.eventType());
	}

	private static NotificationProviderTransientException transientException(ProviderNotificationRequest request,
			Exception cause) {
		return new NotificationProviderTransientException(
				"Notification provider unavailable for event " + request.eventId(), cause);
	}

	private static RestClient buildRestClient(NotificationProviderProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
		requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}

	private static Retry buildRetry(NotificationProviderProperties.Retry properties) {
		IntervalFunction intervalFunction = properties.exponentialBackoffMultiplier() > 1.0
				? IntervalFunction.ofExponentialBackoff(properties.waitDuration(), properties.exponentialBackoffMultiplier())
				: IntervalFunction.of(properties.waitDuration());
		RetryConfig retryConfig = RetryConfig.custom()
				.maxAttempts(properties.maxAttempts())
				.intervalFunction(intervalFunction)
				// Only transient (5xx/timeout/connection) failures are retried; rejections
				// (4xx) and an open circuit breaker fail fast instead.
				.retryOnException(ex -> ex instanceof NotificationProviderTransientException)
				.build();
		return Retry.of("notificationProvider", retryConfig);
	}

	private static CircuitBreaker buildCircuitBreaker(NotificationProviderProperties.CircuitBreaker properties) {
		CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(properties.slidingWindowSize())
				.minimumNumberOfCalls(properties.minimumNumberOfCalls())
				.failureRateThreshold(properties.failureRateThreshold())
				.waitDurationInOpenState(properties.waitDurationInOpenState())
				.permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
				.recordExceptions(NotificationProviderTransientException.class)
				.build();
		return CircuitBreaker.of("notificationProvider", circuitBreakerConfig);
	}
}
