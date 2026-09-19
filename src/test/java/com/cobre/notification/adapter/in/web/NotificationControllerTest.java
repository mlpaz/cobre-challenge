package com.cobre.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cobre.notification.adapter.in.web.config.NotificationEventQueryProperties;
import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;
import com.cobre.notification.domain.port.in.QueryNotificationEventsUseCase;
import com.cobre.notification.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private SendNotificationUseCase sendNotificationUseCase;

	@Mock
	private QueryNotificationEventsUseCase queryNotificationEventsUseCase;

	@Mock
	private ReplayNotificationEventUseCase replayNotificationEventUseCase;

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

	private final UUID notificationEventId = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@BeforeEach
	void setUp() {
		JsonMapper jsonMapper = JsonMapper.builder()
				.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.build();
		NotificationEventQueryProperties queryProperties = new NotificationEventQueryProperties(20, 100, 10000);
		mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(sendNotificationUseCase,
						queryNotificationEventsUseCase, replayNotificationEventUseCase, queryProperties))
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
	void returnsOkWithAFailedStatusWhenDeliveryDefinitelyFails() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.FAILED, null));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("FAILED"));
	}

	@Test
	void returnsOkWithANotSubscribedStatusWhenThereIsNoWebHookUrlForTheEvent() throws Exception {
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.NOT_SUBSCRIBED, null));

		mockMvc.perform(post("/notification_events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_PAYLOAD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("NOT_SUBSCRIBED"));
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

	@Test
	void listsEventsForTheUserInTheHeader() throws Exception {
		NotificationEventRecord record = new NotificationEventRecord(notificationEventId, "EVT001",
				"credit_card_payment", "Payment received", "CLIENT001", Instant.parse("2024-03-15T09:30:22Z"),
				DeliveryStatus.DELIVERED, "ref-123", Instant.parse("2024-03-15T09:31:00Z"));
		given(queryNotificationEventsUseCase.listEvents(any()))
				.willReturn(new PagedResult<>(List.of(record), 1, 20, 0));

		mockMvc.perform(get("/notification_events").header("x-user-id", "CLIENT001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.items[0].event_id").value("EVT001"))
				.andExpect(jsonPath("$.items[0].delivery_status").value("DELIVERED"));
	}

	@Test
	void returnsBadRequestWhenTheUserIdHeaderIsMissingOnList() throws Exception {
		mockMvc.perform(get("/notification_events"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_HEADER"));
	}

	@Test
	void returnsBadRequestWhenLimitExceedsTheConfiguredMaximum() throws Exception {
		mockMvc.perform(get("/notification_events").header("x-user-id", "CLIENT001").param("limit", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
	}

	@Test
	void returnsTheEventWhenTheHeaderMatchesTheOwner() throws Exception {
		NotificationEventRecord record = new NotificationEventRecord(notificationEventId, "EVT001",
				"credit_card_payment", "Payment received", "CLIENT001", Instant.parse("2024-03-15T09:30:22Z"),
				DeliveryStatus.FAILED, null, Instant.parse("2024-03-15T09:31:00Z"));
		given(queryNotificationEventsUseCase.getEvent(eq(notificationEventId), eq("CLIENT001")))
				.willReturn(record);

		mockMvc.perform(get("/notification_events/" + notificationEventId).header("x-user-id", "CLIENT001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("FAILED"));
	}

	@Test
	void returnsBadRequestWhenTheHeaderDoesNotMatchTheEventOwner() throws Exception {
		given(queryNotificationEventsUseCase.getEvent(eq(notificationEventId), eq("OTHER_CLIENT")))
				.willThrow(new NotificationEventAccessDeniedException("mismatch"));

		mockMvc.perform(get("/notification_events/" + notificationEventId).header("x-user-id", "OTHER_CLIENT"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("USER_MISMATCH"));
	}

	@Test
	void returnsNotFoundWhenTheEventDoesNotExist() throws Exception {
		given(queryNotificationEventsUseCase.getEvent(eq(notificationEventId), eq("CLIENT001")))
				.willThrow(new NotificationEventNotFoundException(notificationEventId));

		mockMvc.perform(get("/notification_events/" + notificationEventId).header("x-user-id", "CLIENT001"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_EVENT_NOT_FOUND"));
	}

	@Test
	void replaysAnEventAndReturnsTheNewDeliveryResult() throws Exception {
		given(replayNotificationEventUseCase.replay(eq(notificationEventId), eq("CLIENT001")))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.DELIVERED, "ref-456"));

		mockMvc.perform(post("/notification_events/" + notificationEventId + "/replay")
						.header("x-user-id", "CLIENT001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event_id").value("EVT001"))
				.andExpect(jsonPath("$.delivery_status").value("DELIVERED"))
				.andExpect(jsonPath("$.provider_reference").value("ref-456"));
	}

	@Test
	void returnsBadRequestWhenReplayingAnEventThatBelongsToAnotherUser() throws Exception {
		given(replayNotificationEventUseCase.replay(eq(notificationEventId), eq("OTHER_CLIENT")))
				.willThrow(new NotificationEventAccessDeniedException("mismatch"));

		mockMvc.perform(post("/notification_events/" + notificationEventId + "/replay")
						.header("x-user-id", "OTHER_CLIENT"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("USER_MISMATCH"));
	}
}
