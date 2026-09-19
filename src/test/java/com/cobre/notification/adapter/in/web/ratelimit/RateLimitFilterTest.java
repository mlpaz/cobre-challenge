package com.cobre.notification.adapter.in.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.cobre.notification.adapter.in.web.ratelimit.config.RateLimitProperties;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.build();

	@Test
	void allowsRequestsUpToTheGeneralCapacityThenReturns429() throws Exception {
		RateLimitFilter filter = newFilter(2, 5);

		perform(filter, "GET", "/notification_events");
		MockHttpServletResponse allowed = perform(filter, "GET", "/notification_events");
		MockHttpServletResponse blocked = perform(filter, "GET", "/notification_events");

		assertThat(allowed.getStatus()).isEqualTo(200);
		assertThat(blocked.getStatus()).isEqualTo(429);
		assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
	}

	@Test
	void tracksTheStrictTierSeparatelyFromTheGeneralTierForSubscribe() throws Exception {
		RateLimitFilter filter = newFilter(1, 1);

		// Exhausts the general bucket for this IP.
		perform(filter, "GET", "/notification_events");
		assertThat(perform(filter, "GET", "/notification_events").getStatus()).isEqualTo(429);

		// POST /subscriptions has its own strict bucket, still fresh.
		assertThat(perform(filter, "POST", "/subscriptions").getStatus()).isEqualTo(200);
		assertThat(perform(filter, "POST", "/subscriptions").getStatus()).isEqualTo(429);
	}

	@Test
	void tracksReplaySeparatelyFromSubscribeWithinTheStrictTier() throws Exception {
		RateLimitFilter filter = newFilter(10, 1);

		assertThat(perform(filter, "POST", "/subscriptions").getStatus()).isEqualTo(200);
		assertThat(perform(filter, "POST", "/subscriptions").getStatus()).isEqualTo(429);

		// Independent bucket: replay is still allowed even though subscribe is exhausted.
		assertThat(perform(filter, "POST", "/notification_events/11111111-1111-1111-1111-111111111111/replay")
				.getStatus()).isEqualTo(200);
	}

	@Test
	void doesNotLimitUnrelatedPaths() throws Exception {
		RateLimitFilter filter = newFilter(0, 0);

		assertThat(perform(filter, "GET", "/health").getStatus()).isEqualTo(200);
		assertThat(perform(filter, "GET", "/v3/api-docs").getStatus()).isEqualTo(200);
	}

	private RateLimitFilter newFilter(int generalCapacity, int strictCapacity) {
		RateLimitProperties properties = new RateLimitProperties(
				new RateLimitProperties.Tier(generalCapacity, generalCapacity, Duration.ofMinutes(1)),
				new RateLimitProperties.Tier(strictCapacity, strictCapacity, Duration.ofMinutes(1)));
		return new RateLimitFilter(properties, jsonMapper);
	}

	private static MockHttpServletResponse perform(RateLimitFilter filter, String method, String path)
			throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("203.0.113.10");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> { });
		return response;
	}
}
