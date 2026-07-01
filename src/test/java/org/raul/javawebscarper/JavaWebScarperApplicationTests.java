package org.raul.javawebscarper;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.raul.javawebscarper.service.AuthorService;
import org.raul.javawebscarper.service.KeywordService;
import org.raul.javawebscarper.service.PostService;
import org.raul.javawebscarper.service.ScrapeJobService;
import org.raul.javawebscarper.service.SourceService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
		"spring.jpa.hibernate.ddl-auto=none"
})
class JavaWebScarperApplicationTests {

	@MockitoBean
	private SourceService sourceService;

	@MockitoBean
	private KeywordService keywordService;

	@MockitoBean
	private AuthorService authorService;

	@MockitoBean
	private PostService postService;

	@MockitoBean
	private ScrapeJobService scrapeJobService;

	@MockitoBean
	private ScrapeJobOrchestrator scrapeJobOrchestrator;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void contextLoads() {
	}

}
