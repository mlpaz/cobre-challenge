package com.cobre.notification.adapter.in.web.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cobre.notification.adapter.in.web.dto.ErrorResponse;
import com.cobre.notification.adapter.in.web.ratelimit.config.RateLimitProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rate limits the public API by caller IP, now that there's no authentication
 * to key on (see the Seguridad section in the README — A05). Two tiers:
 * {@code general} for every request under /notification_events and
 * /subscriptions, and a stricter {@code strict} tier — tracked separately per
 * endpoint — for POST /subscriptions and POST /notification_events/{id}/replay.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private static final String REPLAY_PATH_PATTERN = "/notification_events/[^/]+/replay";

	private final RateLimiter generalLimiter;
	private final RateLimiter subscriptionsLimiter;
	private final RateLimiter replayLimiter;
	private final JsonMapper jsonMapper;

	public RateLimitFilter(RateLimitProperties properties, JsonMapper jsonMapper) {
		this.generalLimiter = newLimiter(properties.general());
		this.subscriptionsLimiter = newLimiter(properties.strict());
		this.replayLimiter = newLimiter(properties.strict());
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		RateLimiter limiter = resolveLimiter(request.getMethod(), request.getRequestURI());
		if (limiter != null && !limiter.tryConsume(callerKey(request))) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType("application/json");
			response.getWriter()
					.write(jsonMapper.writeValueAsString(
							new ErrorResponse("RATE_LIMITED", "Too many requests, try again later")));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private RateLimiter resolveLimiter(String method, String path) {
		if ("POST".equals(method) && "/subscriptions".equals(path)) {
			return subscriptionsLimiter;
		}
		if ("POST".equals(method) && path.matches(REPLAY_PATH_PATTERN)) {
			return replayLimiter;
		}
		if (path.startsWith("/notification_events") || path.startsWith("/subscriptions")) {
			return generalLimiter;
		}
		// Anything else (health, Swagger UI, the OpenAPI doc itself) is not rate limited.
		return null;
	}

	/** First hop of X-Forwarded-For when present (behind a proxy/load balancer), else the socket's own address. */
	private static String callerKey(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static RateLimiter newLimiter(RateLimitProperties.Tier tier) {
		return new RateLimiter(tier.capacity(), tier.refillTokens(), tier.refillPeriod());
	}
}
