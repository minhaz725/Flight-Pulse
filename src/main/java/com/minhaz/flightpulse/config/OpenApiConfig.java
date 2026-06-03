package com.minhaz.flightpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI flightPulseOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("FlightPulse API")
						.description("backend service that watches flight deal sources, scores deals, and matches them against user subscriptions")
						.version("v0.1.0")
						.license(new License().name("MIT")));
	}
}
