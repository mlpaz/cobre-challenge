package com.cobre.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.SubscriptionNotConfirmedException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private SendNotificationUseCase sendNotificationUseCase;

	private MockMvc mockMvc;

	private static final String VALID_PAYLOAD = """
			{
			  "event_id": "EVT001",
			  "event_type": "credit_card_payment",
			  "content": "Credit card payment received for $150.00",
			  "delivery_date": "2024-03-15T09:30:22Z",
			  "client_id": "CLIENT001"
			}
			""";

	@BeforeEach
	void setUp() {
		JsonMapper jsonMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build();
		mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(sendNotificationUseCase))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
				.build();
	}

	@Test
	void returnsOkWithTheDeliveryResultWhenDeliverySucceeds() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.DELIVERED, "ref-123"));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("DELIVERED"))
				.andExpect(jsonPath("$.provider_reference").value("ref-123"));
	}

	@Test
	void returnsOkWithADuplicateStatusWhenTheEventWasAlreadyDelivered() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.DUPLICATE, null));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("DUPLICATE"));
	}

	@Test
	void returnsServiceUnavailableWhenDeliveryFails() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willThrow(new NotificationDeliveryException("provider circuit breaker is open", null));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_DELIVERY_FAILED"));
	}

	@Test
	void returnsForbiddenWhenThereIsNoActiveSubscriptionForTheEvent() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willThrow(new SubscriptionNotConfirmedException("no active subscription"));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_CONFIRMED"));
	}

	@Test
	void returnsBadRequestWhenTheRequestIsInvalid() throws Exception {
		String invalidPayload = """
				{
				  "event_type": "credit_card_payment",
				  "content": "Credit card payment received for $150.00",
				  "delivery_date": "2024-03-15T09:30:22Z",
				  "client_id": "CLIENT001"
				}
				""";

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalidPayload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
