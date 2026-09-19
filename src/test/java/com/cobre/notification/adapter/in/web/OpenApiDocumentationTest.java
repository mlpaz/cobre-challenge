package com.cobre.notification.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Proves SpringDoc actually generates a working OpenAPI document against this
 * app's real controllers and Jackson setup, not just that the context loads
 * with the dependency on the classpath. Boot 4's spring-boot-test-autoconfigure
 * dropped {@code @AutoConfigureMockMvc} (same gap noted in
 * NotificationRecordJpaAdapterTest for @DataJpaTest), so MockMvc is built
 * directly from the real WebApplicationContext instead.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:notification-openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
class OpenApiDocumentationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Test
	void exposesTheGeneratedOpenApiDocumentWithTheNotificationEventsPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"/notification_events\"")))
				.andExpect(content().string(containsString("\"/notification_events/{notification_event_id}/replay\"")))
				.andExpect(content().string(containsString("\"/subscriptions\"")));
	}

	@Test
	void servesTheSwaggerUi() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void redirectsTheShortSwaggerUiUrl() throws Exception {
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
	}
}
