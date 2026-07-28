package org.raul.javawebscarper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI javaWebScarperOpenApi() {
		return new OpenAPI()
				.components(new Components().addSecuritySchemes("bearerAuth",
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
				.info(new Info()
						.title("Java Web Scarper API")
						.description("News and social publication scraping backend API")
						.version("1.0")
						.contact(new Contact()
								.name("Raul Alishov")
								.email("alishov7394@gmail.com")
								.url("https://github.com/aliishov/java-web-scarper")));
	}
}
