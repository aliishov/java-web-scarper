# Instagram authentication

Instagram scraping uses a reusable Playwright storage state. The backend does not store Instagram usernames, emails, phone numbers, passwords, 2FA secrets, or cookies in source code, configuration, tests, or logs.

## Initial authentication

Set the storage state path outside the repository:

```powershell
$env:INSTAGRAM_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\instagram-storage-state.json"
```

Run the interactive task:

```powershell
.\gradlew.bat instagramAuthStateInteractive
```

The task opens headed Chromium. Complete Instagram login manually in the browser, including 2FA, challenge, CAPTCHA, consent, or optional dialogs if Instagram shows them. Do not type credentials into the terminal. Do not close the browser before the utility reports completion.

After the authenticated state is detected, the utility writes a temporary storage state, validates it in a new BrowserContext, and only then replaces the configured target file.

## Application startup

Use the same environment variable before running the app:

```powershell
$env:INSTAGRAM_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\instagram-storage-state.json"
.\gradlew.bat bootRun
```

If `scraper.instagram.enabled=false`, the app starts without `INSTAGRAM_AUTH_STATE_PATH`.

If Instagram is enabled but the state is missing or expired, the Spring context still starts. Only Instagram jobs fail with a clear authentication error.

## Refreshing state

Regenerate the state when a job returns:

- `INSTAGRAM_AUTH_STATE_EXPIRED`
- `INSTAGRAM_CHALLENGE_REQUIRED`
- `INSTAGRAM_TWO_FACTOR_REQUIRED`
- `INSTAGRAM_CONSENT_REQUIRED`
- `INSTAGRAM_RATE_LIMITED`
- `INSTAGRAM_ACCOUNT_RESTRICTED`

Run:

```powershell
.\gradlew.bat instagramAuthStateInteractive
```

## Security

The storage state contains sensitive session cookies. Never commit it, attach it to bug reports, paste it into chat, print it with `Get-Content`, or share it with other people. If it leaks, terminate active Instagram sessions and create a new storage state.

Use separate variables for each platform:

- `X_AUTH_STATE_PATH`
- `FACEBOOK_AUTH_STATE_PATH`
- `INSTAGRAM_AUTH_STATE_PATH`

Do not reuse Facebook storage state as Instagram storage state.
