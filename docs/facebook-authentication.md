# Facebook Authentication

Facebook scraping uses a reusable Playwright storage state. The backend never stores or reads a Facebook login or password.

## Initial Login

Set the storage state path outside the Git repository:

```powershell
$env:FACEBOOK_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\facebook-storage-state.json"
.\gradlew.bat facebookAuthStateInteractive
```

The task opens a headed Chromium window. Sign in to Facebook manually and complete any 2FA, CAPTCHA, checkpoint, or suspicious-login confirmation in that browser. Keep the window open until the task prints that the authentication state was saved successfully.

Optional local settings:

```powershell
$env:FACEBOOK_MANUAL_VERIFICATION_TIMEOUT_MS = "300000"
$env:FACEBOOK_LOGIN_URL = "https://www.facebook.com/login"
$env:FACEBOOK_AUTH_VERIFICATION_URL = "https://www.facebook.com/"
$env:FACEBOOK_LOCALE = "en-US"
$env:FACEBOOK_TIMEZONE_ID = "Asia/Baku"
```

Do not pass passwords or account identifiers as command-line arguments or environment variables.

## Application Runtime

Use the same storage state path when running the application:

```powershell
$env:FACEBOOK_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\facebook-storage-state.json"
.\gradlew.bat bootRun
```

Each Facebook scraping job creates a fresh Playwright `BrowserContext` with this storage state. X.com and Facebook use separate paths (`X_AUTH_STATE_PATH` and `FACEBOOK_AUTH_STATE_PATH`), and regular news scrapers continue to run without any storage state.

## Refreshing State

Regenerate the state when a Facebook job reports one of these errors:

- `FACEBOOK_AUTH_STATE_MISSING`
- `FACEBOOK_AUTH_STATE_EXPIRED`
- `FACEBOOK_CHECKPOINT_REQUIRED`
- `FACEBOOK_TWO_FACTOR_REQUIRED`
- `FACEBOOK_CHALLENGE_REQUIRED`

Run:

```powershell
.\gradlew.bat facebookAuthStateInteractive
```

## Security Rules

The storage state contains sensitive session cookies. Do not share it, commit it, attach it to bug reports, print it in terminal output, or copy it into documentation.

The repository ignores common state file patterns:

- `playwright/.auth/`
- `**/facebook-auth*.json`
- `**/facebook-storage-state*.json`
- `**/auth-state*.json`
- `**/storage-state*.json`

If a state file is exposed, sign out of Facebook sessions and create a fresh storage state.

## Manual QA Checklist

1. Run `.\gradlew.bat clean test`.
2. Run `.\gradlew.bat clean build`.
3. Set `FACEBOOK_AUTH_STATE_PATH` outside the repository.
4. Run `.\gradlew.bat facebookAuthStateInteractive`.
5. Confirm with `Test-Path $env:FACEBOOK_AUTH_STATE_PATH`.
6. Do not run `Get-Content $env:FACEBOOK_AUTH_STATE_PATH`.
7. Start the application with the same environment variable.
8. Run a Facebook scraping job and confirm it does not ask for login again.
9. Run a second Facebook scraping job and confirm the saved state is reused.

## Known Limitations

- Facebook can expire the session at any time.
- 2FA, CAPTCHA, and checkpoints must be completed manually.
- The utility does not bypass Meta security controls.
- Meta can temporarily rate-limit or block automation.
- Reels are disabled by default in the scraper.
