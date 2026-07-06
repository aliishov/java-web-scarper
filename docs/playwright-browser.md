# Playwright Browser Engine

The backend includes Playwright Java for future browser-based scraper adapters.
Current scraper adapters are not implemented here, and scraped posts are not persisted by this layer.

Browser binaries are not installed automatically during application startup.
Install Chromium explicitly for local development before enabling real browser-based scrapers:

```bash
./gradlew playwrightInstall
```

If the Gradle helper is not available in the environment, run the Playwright CLI from the resolved runtime classpath:

```bash
java -cp "<project-runtime-classpath>" com.microsoft.playwright.CLI install chromium
```

The current Dockerfile intentionally keeps the runtime image small and does not bundle browser binaries.
When real browser scrapers are added, introduce a dedicated Playwright-ready image layer with the required
system packages and browser installation step.
