package com.cobre.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.InvalidEventTypeException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationEventKafkaListenerTest {

	@Mock
	private SendNotificationUseCase sendNotificationUseCase;

	private final JsonMapper jsonMapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();

	private static final String VALID_PAYLOAD = """
			{
			  "event_id": "EVT001",
			  "event_type": "credit_card_payment",
			  "content": "Credit card payment received for $150.00",
			  "delivery_date": "2024-03-15T09:30:22Z",
			  "client_id": "CLIENT001"
			}
			""";

	@Test
	void mapsTheRecordAndInvokesTheUseCase() {
		NotificationEventKafkaListener listener = new NotificationEventKafkaListener(sendNotificationUseCase, jsonMapper);
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.DELIVERED, "ref-1"));

		listener.onMessage(record(VALID_PAYLOAD));

		verify(sendNotificationUseCase).sendNotification(new NotificationEvent("EVT001", "credit_card_payment",
				"Credit card payment received for $150.00",
				java.time.Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001"));
	}

	@Test
	void doesNotThrowWhenDeliveryDefinitelyFailed() {
		NotificationEventKafkaListener listener = new NotificationEventKafkaListener(sendNotificationUseCase, jsonMapper);
		given(sendNotificationUseCase.sendNotification(any()))
				.willReturn(new DeliveryResult("EVT001", DeliveryStatus.FAILED, null));

		assertThatCode(() -> listener.onMessage(record(VALID_PAYLOAD))).doesNotThrowAnyException();
	}

	@Test
	void propagatesOnAMalformedMessageSoTheContainerErrorHandlerCanRetryIt() {
		NotificationEventKafkaListener listener = new NotificationEventKafkaListener(sendNotificationUseCase, jsonMapper);

		assertThatThrownBy(() -> listener.onMessage(record("not-json")))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void propagatesOnAnUnknownEventTypeSoTheContainerErrorHandlerCanRetryIt() {
		String payloadWithUnknownEventType = """
				{
				  "event_id": "EVT001",
				  "event_type": "banana_payment",
				  "content": "Credit card payment received for $150.00",
				  "delivery_date": "2024-03-15T09:30:22Z",
				  "client_id": "CLIENT001"
				}
				""";
		NotificationEventKafkaListener listener = new NotificationEventKafkaListener(sendNotificationUseCase, jsonMapper);

		assertThatThrownBy(() -> listener.onMessage(record(payloadWithUnknownEventType)))
				.isInstanceOf(InvalidEventTypeException.class);
	}

	private static ConsumerRecord<String, String> record(String payload) {
		return new ConsumerRecord<>("notification-events", 0, 0L, "EVT001", payload);
	}
}
