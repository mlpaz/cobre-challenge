package com.cobre.notification.adapter.out.subscription;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.cobre.notification.adapter.out.subscription.config.SubscriptionServiceProperties;

/**
 * Technical HTTP client for the subscription registry: confirms whether a
 * client is actively subscribed to a given event type.
 */
public class SubscriptionHttpClient {

	private final RestClient restClient;
	private final String path;

	public SubscriptionHttpClient(SubscriptionServiceProperties properties) {
		this(buildRestClient(properties), properties.path());
	}

	/** Visible for tests: allows binding a {@link RestClient} to a mock HTTP server. */
	SubscriptionHttpClient(RestClient restClient, String path) {
		this.restClient = restClient;
		this.path = path;
	}

	public boolean isActive(String clientId, String eventType) {
		try {
			restClient.get()
					.uri(uriBuilder -> uriBuilder.path(path)
							.queryParam("client_id", clientId)
							.queryParam("event_type", eventType)
							.build())
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (HttpClientErrorException.NotFound e) {
			return false;
		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			throw new SubscriptionServiceUnavailableException(
					"Could not confirm subscription for client " + clientId + " and event type " + eventType, e);
		}
	}

	private static RestClient buildRestClient(SubscriptionServiceProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
		requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}
}
