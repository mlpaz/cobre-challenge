package com.cobre.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cobre.notification.domain.exception.SubscriptionNotFoundException;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.SubscriptionStatus;
import com.cobre.notification.domain.port.in.DeleteSubscriptionUseCase;
import com.cobre.notification.domain.port.in.QuerySubscriptionsUseCase;
import com.cobre.notification.domain.port.in.SubscribeUseCase;
import com.cobre.notification.domain.port.in.UpdateSubscriptionUseCase;
import com.cobre.notification.domain.port.out.MetricsPort;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

	private static final String VALID_PAYLOAD = """
			{
			  "event_type": "credit_card_payment",
			  "web_hook_url": "https://client.example.com/webhooks/notifications"
			}
			""";

	@Mock
	private SubscribeUseCase subscribeUseCase;

	@Mock
	private UpdateSubscriptionUseCase updateSubscriptionUseCase;

	@Mock
	private QuerySubscriptionsUseCase querySubscriptionsUseCase;

	@Mock
	private DeleteSubscriptionUseCase deleteSubscriptionUseCase;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		JsonMapper jsonMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build();
		mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscribeUseCase,
						updateSubscriptionUseCase, querySubscriptionsUseCase, deleteSubscriptionUseCase))
				.setControllerAdvice(new GlobalExceptionHandler(Mockito.mock(MetricsPort.class)))
				.setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
				.build();
	}

	@Test
	void createsTheSubscriptionForTheUserInTheHeaderAndReturnsCreated() throws Exception {
		mockMvc.perform(post("/subscriptions")
						.header("x-user-id", "CLIENT001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").value("CLIENT001"))
				.andExpect(jsonPath("$.event_type").value("credit_card_payment"))
				.andExpect(jsonPath("$.web_hook_url").value("https://client.example.com/webhooks/notifications"));

		verify(subscribeUseCase).subscribe(eq(new Subscription("CLIENT001", "credit_card_payment",
				"https://client.example.com/webhooks/notifications")));
	}

	@Test
	void returnsBadRequestWhenTheUserIdHeaderIsMissing() throws Exception {
		mockMvc.perform(post("/subscriptions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_HEADER"));
	}

	@Test
	void returnsBadRequestWhenTheWebHookUrlIsNotAValidUrl() throws Exception {
		String payload = """
				{
				  "event_type": "credit_card_payment",
				  "web_hook_url": "not-a-url"
				}
				""";

		mockMvc.perform(post("/subscriptions")
						.header("x-user-id", "CLIENT001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsBadRequestWhenRequiredFieldsAreMissing() throws Exception {
		String payload = """
				{
				  "web_hook_url": "https://client.example.com/webhooks/notifications"
				}
				""";

		mockMvc.perform(post("/subscriptions")
						.header("x-user-id", "CLIENT001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void listsTheSubscriptionsForTheUserInTheHeader() throws Exception {
		given(querySubscriptionsUseCase.listByUserId("CLIENT001")).willReturn(List.of(
				new SubscriptionStatus("CLIENT001", "credit_card_payment", "https://client.example.com/webhooks/a",
						100, false),
				new SubscriptionStatus("CLIENT001", "debit_card_withdrawal", "https://client.example.com/webhooks/b",
						26, true)));

		mockMvc.perform(get("/subscriptions").header("x-user-id", "CLIENT001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].event_type").value("credit_card_payment"))
				.andExpect(jsonPath("$[0].web_hook_url").value("https://client.example.com/webhooks/a"))
				.andExpect(jsonPath("$[0].success_score").value(100))
				.andExpect(jsonPath("$[0].open").value(false))
				.andExpect(jsonPath("$[1].event_type").value("debit_card_withdrawal"))
				.andExpect(jsonPath("$[1].success_score").value(26))
				.andExpect(jsonPath("$[1].open").value(true));
	}

	@Test
	void returnsBadRequestWhenListingWithoutTheUserIdHeader() throws Exception {
		mockMvc.perform(get("/subscriptions"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_HEADER"));
	}

	@Test
	void updatesAnExistingSubscriptionAndReturnsOk() throws Exception {
		String payload = """
				{
				  "web_hook_url": "https://client.example.com/webhooks/new"
				}
				""";

		mockMvc.perform(put("/subscriptions/credit_card_payment")
						.header("x-user-id", "CLIENT001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user_id").value("CLIENT001"))
				.andExpect(jsonPath("$.event_type").value("credit_card_payment"))
				.andExpect(jsonPath("$.web_hook_url").value("https://client.example.com/webhooks/new"));

		verify(updateSubscriptionUseCase).update(new Subscription("CLIENT001", "credit_card_payment",
				"https://client.example.com/webhooks/new"));
	}

	@Test
	void returnsNotFoundWhenUpdatingASubscriptionThatDoesNotExist() throws Exception {
		String payload = """
				{
				  "web_hook_url": "https://client.example.com/webhooks/new"
				}
				""";
		Mockito.doThrow(new SubscriptionNotFoundException("CLIENT001", "credit_card_payment"))
				.when(updateSubscriptionUseCase).update(org.mockito.ArgumentMatchers.any());

		mockMvc.perform(put("/subscriptions/credit_card_payment")
						.header("x-user-id", "CLIENT001")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
	}

	@Test
	void returnsBadRequestWhenUpdatingWithoutTheUserIdHeader() throws Exception {
		String payload = """
				{
				  "web_hook_url": "https://client.example.com/webhooks/new"
				}
				""";

		mockMvc.perform(put("/subscriptions/credit_card_payment")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_HEADER"));
	}

	@Test
	void deletesAnExistingSubscriptionAndReturnsNoContent() throws Exception {
		mockMvc.perform(delete("/subscriptions/credit_card_payment")
						.header("x-user-id", "CLIENT001"))
				.andExpect(status().isNoContent());

		verify(deleteSubscriptionUseCase).delete("CLIENT001", "credit_card_payment");
	}

	@Test
	void returnsNotFoundWhenDeletingASubscriptionThatDoesNotExist() throws Exception {
		Mockito.doThrow(new SubscriptionNotFoundException("CLIENT001", "credit_card_payment"))
				.when(deleteSubscriptionUseCase).delete("CLIENT001", "credit_card_payment");

		mockMvc.perform(delete("/subscriptions/credit_card_payment")
						.header("x-user-id", "CLIENT001"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
	}

	@Test
	void returnsBadRequestWhenDeletingWithoutTheUserIdHeader() throws Exception {
		mockMvc.perform(delete("/subscriptions/credit_card_payment"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_HEADER"));
	}
}
