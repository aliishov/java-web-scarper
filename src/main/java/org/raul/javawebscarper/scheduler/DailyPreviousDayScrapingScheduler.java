package org.raul.javawebscarper.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.DailyScrapingProperties;
import org.raul.javawebscarper.dto.response.scrapejob.DailyScrapeRunResponseDTO;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPreviousDayScrapingScheduler {

	private final DailyScrapingProperties properties;
	private final ScrapeJobOrchestrator orchestrator;

	@Scheduled(cron = "${scraper.daily.cron}", zone = "${scraper.daily.zone-id:Asia/Baku}")
	public void runDailyPreviousDayScraping() {
		runScrapingCycle("cron");
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!properties.isRunOnStartup()) {
			return;
		}
		runScrapingCycle("startup");
	}

	private void runScrapingCycle(String trigger) {
		if (!properties.isEnabled()) {
			log.debug("Daily previous-day scraping is disabled; trigger={} skipped", trigger);
			return;
		}
		try {
			log.info("Daily previous-day scraping triggered: trigger={}", trigger);
			DailyScrapeRunResponseDTO summary = orchestrator.createAndRunDailyPreviousDayJobs();
			log.info(
					"Daily previous-day scraping completed: trigger={}, dateFrom={}, dateTo={}, zoneId={}, "
							+ "jobsCreated={}, jobsSucceeded={}, jobsFailed={}, jobsSkipped={}, "
							+ "postsFound={}, postsSaved={}",
					trigger,
					summary.dateFrom(),
					summary.dateTo(),
					summary.zoneId(),
					summary.jobsCreated(),
					summary.jobsSucceeded(),
					summary.jobsFailed(),
					summary.jobsSkipped(),
					summary.postsFound(),
					summary.postsSaved()
			);
		} catch (Exception exception) {
			log.error("Daily previous-day scraping failed: trigger={}", trigger, exception);
		}
	}
}
