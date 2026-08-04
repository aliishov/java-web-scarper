package org.raul.javawebscarper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.raul.javawebscarper.service.AuthorService;
import org.raul.javawebscarper.service.AdminService;
import org.raul.javawebscarper.service.AuthService;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.PostService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.ScrapedPostIngestionService;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
		"spring.jpa.hibernate.ddl-auto=none",
		"security.jwt.secret=test-secret-that-is-longer-than-thirty-two-characters",
		"springdoc.api-docs.enabled=true",
		"springdoc.api-docs.path=/v3/api-docs",
		"springdoc.swagger-ui.enabled=true",
		"springdoc.swagger-ui.path=/swagger-ui.html"
})
class OpenApiDocumentationTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@MockitoBean
	private SourceService sourceService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private AdminService adminService;

	@MockitoBean
	private KeywordService keywordService;

	@MockitoBean
	private AuthorService authorService;

	@MockitoBean
	private PostService postService;

	@MockitoBean
	private ScrapeJobService scrapeJobService;

	@MockitoBean
	private ScrapedPostIngestionService scrapedPostIngestionService;

	@MockitoBean
	private ScrapeJobOrchestrator scrapeJobOrchestrator;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Test
	void apiDocsAreGenerated() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.paths['/api/sources']").exists());
	}

	@Test
	void swaggerUiIsAvailable() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Swagger UI")));
	}
}
