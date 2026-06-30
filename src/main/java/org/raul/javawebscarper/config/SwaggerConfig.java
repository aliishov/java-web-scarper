package org.raul.javawebscarper.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
		info = @Info(
				contact = @Contact(
						name = "Raul Alishov",
						email = "alishov7394@gmail.com",
						url = "https://github.com/aliishov/java-web-scarper"
				),
				description = "News and social publication scraping backend API",
				title = "Java Web Scarper API",
				version = "1.0"
		)
)
public class SwaggerConfig {
}
