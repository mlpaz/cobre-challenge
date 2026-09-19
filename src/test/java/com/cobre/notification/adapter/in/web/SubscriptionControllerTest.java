package com.cobre.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.SubscribeUseCase;
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

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		JsonMapper jsonMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build();
		mockMvc = MockMvcBuilders.standaloneSetup(new SubscriptionController(subscribeUseCase))
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
}
