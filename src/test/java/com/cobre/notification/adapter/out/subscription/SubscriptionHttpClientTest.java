package com.cobre.notification.adapter.out.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SubscriptionHttpClientTest {

	@Test
	void returnsTrueWhenTheRegistryConfirmsAnActiveSubscription() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://subscription.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://subscription.local/subscriptions?client_id=CLIENT001&event_type=credit_card_payment"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess());

		SubscriptionHttpClient client = new SubscriptionHttpClient(builder.build(), "/subscriptions");

		assertThat(client.isActive("CLIENT001", "credit_card_payment")).isTrue();
		server.verify();
	}

	@Test
	void returnsFalseWhenTheRegistryHasNoMatchingSubscription() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://subscription.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://subscription.local/subscriptions?client_id=CLIENT002&event_type=credit_card_payment"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		SubscriptionHttpClient client = new SubscriptionHttpClient(builder.build(), "/subscriptions");

		assertThat(client.isActive("CLIENT002", "credit_card_payment")).isFalse();
		server.verify();
	}

	@Test
	void throwsWhenTheRegistryIsUnavailable() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://subscription.local");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://subscription.local/subscriptions?client_id=CLIENT001&event_type=credit_card_payment"))
				.andRespond(withServerError());

		SubscriptionHttpClient client = new SubscriptionHttpClient(builder.build(), "/subscriptions");

		assertThatThrownBy(() -> client.isActive("CLIENT001", "credit_card_payment"))
				.isInstanceOf(SubscriptionServiceUnavailableException.class);
		server.verify();
	}
}
