# X Authentication Setup

The X scraper does not store credentials and does not perform username/password login in application code.
If X requires an authenticated browser session, provide a Playwright storage-state JSON file through configuration.

## Configuration

Use one of these options:

- Environment variable: `X_AUTH_STATE_PATH`
- YAML property: `scraper.x.auth-state-path`

Example local value:

```yaml
scraper:
  x:
    auth-state-path: ${X_AUTH_STATE_PATH:}
```

Recommended local path:

```text
playwright/.auth/x-storage-state.json
```

The project ignores the following auth-state files:

- `playwright/.auth/`
- `auth-state*.json`
- `x-storage-state*.json`

Never commit cookies, local storage, screenshots, HTML dumps, passwords, or MFA recovery data.

## Creating Storage State

Create the storage state outside the application by opening X in a Playwright-controlled browser, signing in manually, and saving the browser context state.
Keep the resulting JSON file on the local machine or in a secure runtime secret store.

Minimal standalone Playwright Java flow:

```java
try (Playwright playwright = Playwright.create()) {
    Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
    BrowserContext context = browser.newContext();
    Page page = context.newPage();
    page.navigate("https://x.com/");
    // Sign in manually, including MFA if required.
    context.storageState(new BrowserContext.StorageStateOptions()
            .setPath(Paths.get("playwright/.auth/x-storage-state.json")));
}
```

After saving the file, run the backend with:

```powershell
$env:X_AUTH_STATE_PATH="playwright/.auth/x-storage-state.json"
```

## Runtime Behavior

- If `scraper.x.auth-state-path` is blank, the scraper starts an anonymous browser session.
- If the configured file does not exist, the X scraper returns `FAILED` with an `AUTH_STATE_EXPIRED` message.
- If X shows a login wall, rate limit, or timeline error page, the scraper returns `FAILED`.
- Authenticated sessions can expire at any time; refresh the storage-state file when jobs start failing with login/auth messages.

## Operational Notes

- Use a dedicated scraping account and follow X platform rules and rate limits.
- Rotate or delete storage-state files when access is no longer needed.
- Do not share auth-state files in chat, pull requests, issue comments, logs, or test fixtures.
- Keep live HTML/screenshot debugging artifacts outside the repository unless they are sanitized and explicitly required.
