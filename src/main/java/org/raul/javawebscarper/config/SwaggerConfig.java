package org.raul.javawebscarper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI javaWebScarperOpenApi() {
		return new OpenAPI()
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
