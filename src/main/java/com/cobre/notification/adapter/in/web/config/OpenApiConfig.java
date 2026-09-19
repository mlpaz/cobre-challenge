package com.cobre.notification.adapter.in.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Metadata for the SpringDoc-generated OpenAPI document, served at
 * {@code /v3/api-docs} (JSON) and browsable at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI notificationServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Notification Service API")
						.description("Recibe eventos de la plataforma, los entrega al webhook suscripto de cada "
								+ "cliente a través del Notification Provider, y expone un historial consultable "
								+ "con soporte de replay.")
						.version("v1"));
	}
}
