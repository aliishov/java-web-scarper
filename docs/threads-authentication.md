# Threads Authentication

Threads scraping supports anonymous browsing by default and an optional reusable Playwright storage-state file. The backend never reads or stores a Threads/Instagram password.

## Runtime configuration

Configuration is available through Spring properties or environment variables:

```yaml
scraper:
  threads:
    enabled: true
    base-url: https://www.threads.com
    auth-state-path: ${THREADS_AUTH_STATE_PATH:}
    authentication-required: false
```

For authenticated operation, create a storage state outside this repository with a trusted local Playwright login flow, then run the application with:

```powershell
$env:THREADS_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\threads-storage-state.json"
.\gradlew.bat bootRun
```

Do not print, share, commit, or attach the storage-state JSON. It contains sensitive session cookies. Threads and Instagram states should be kept in separate files even when Meta links the profiles.

## Failure states

- `THREADS_AUTH_STATE_MISSING`: required state is absent or unreadable.
- `THREADS_LOGIN_REQUIRED`: anonymous access is insufficient or the state expired.
- `THREADS_CHALLENGE_REQUIRED`: manual security verification is required.
- `THREADS_RATE_LIMITED`: access was temporarily limited.

The scraper does not bypass login, CAPTCHA, checkpoints, challenges, or rate limits.

## Manual QA

1. Run the test suite and build.
2. Start anonymously and verify a public keyword search.
3. If anonymous browsing is limited, provide a storage state outside the repository.
4. Verify canonical `/@username/post/{postId}` URLs, author, timestamp, text, and scoped media.
5. Verify that replies and reposts are excluded by default.
6. Confirm cookies, storage state, full HTML, and post text are absent from logs.
