package org.raul.javawebscarper.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.config.ScrapeSchedulerProperties;
import org.raul.javawebscarper.dto.response.scrapejob.ScheduledScrapeRunResponseDTO;
import org.raul.javawebscarper.orchestrator.ScrapeJobOrchestrator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapeJobScheduler {

	private final ScrapeSchedulerProperties properties;
	private final ScrapeJobOrchestrator orchestrator;

	@Scheduled(cron = "${scraper.scheduler.cron}")
	public void runScheduledScraping() {
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
			log.debug("Scrape scheduler is disabled; trigger={} skipped", trigger);
			return;
		}
		try {
			log.info("Scrape scheduler triggered: trigger={}", trigger);
			ScheduledScrapeRunResponseDTO summary = orchestrator.createAndRunScheduledJobs();
			log.info(
					"Scrape scheduler completed: trigger={}, jobsCreated={}, jobsSucceeded={}, jobsFailed={}, jobsSkipped={}",
					trigger,
					summary.jobsCreated(),
					summary.jobsSucceeded(),
					summary.jobsFailed(),
					summary.jobsSkipped()
			);
		} catch (Exception exception) {
			log.error("Scrape scheduler failed: trigger={}", trigger, exception);
		}
	}
}
