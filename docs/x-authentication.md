# X Authentication Setup

The X scraper uses a reusable Playwright storage-state file. It must not keep usernames, emails, passwords, cookies, tokens, or storage-state JSON in Java code, YAML, tests, docs, logs, Gradle files, or Git history.

If a password was shared in chat or logs, rotate it before creating a storage state.

## Runtime Configuration

The application reads only the storage-state path:

```yaml
scraper:
  x:
    auth-state-path: ${X_AUTH_STATE_PATH:}
    authentication-required: true
```

Runtime scraping does not perform username/password login. If `authentication-required=true` and `X_AUTH_STATE_PATH` is missing, invalid, expired, or redirected to login, the X job fails with an auth-specific error and asks you to regenerate the state.

## Creating Storage State

Preferred local flow:

```powershell
$env:X_LOGIN = "your-username-or-email"
$env:X_USERNAME = "your-username"
$env:X_AUTH_STATE_PATH = "playwright/.auth/x-storage-state.json"

.\gradlew.bat xAuthState
```

The utility reads the password interactively when a console is available. If there is no interactive console, set `X_PASSWORD` only for the child process session and remove it immediately after use:

```powershell
$env:X_PASSWORD = Read-Host "Enter X password"
.\gradlew.bat xAuthState
Remove-Item Env:X_PASSWORD
```

Clean up non-secret identifiers from the shell when done:

```powershell
Remove-Item Env:X_LOGIN
Remove-Item Env:X_USERNAME
```

The utility prints only safe status messages. It must not print credentials, cookies, tokens, or storage-state JSON.

## Interactive Fallback

If automated form filling triggers a challenge, use the manual headed-browser flow:

```powershell
$env:X_AUTH_STATE_PATH = "playwright/.auth/x-storage-state.json"
.\gradlew.bat xAuthStateInteractive
```

Sign in manually in the opened browser. Complete MFA, email verification, CAPTCHA, or other security prompts yourself. The utility waits for an authenticated page and saves state only after authentication is confirmed.

## Running The App

```powershell
$env:X_AUTH_STATE_PATH = "playwright/.auth/x-storage-state.json"
.\gradlew.bat bootRun
```

For Docker Compose, pass `X_AUTH_STATE_PATH` through the environment and mount/provide the file securely. Do not bake storage-state files into the image.

## Refreshing Expired State

Regenerate state when scraping returns one of these messages:

- `AUTH_STATE_EXPIRED`
- `AUTH_REQUIRED`
- `CHALLENGE_REQUIRED`

Use either `xAuthState` or `xAuthStateInteractive`, depending on whether X allows automated form filling.

## Ignored Files

The project ignores local auth artifacts:

- `.env`
- `.env.*`
- `playwright/.auth/`
- `auth-state*.json`
- `x-storage-state*.json`
- `**/auth-state*.json`
- `**/storage-state*.json`
- `**/x-auth*.json`

Only `.env.example` may be committed, and it must not contain real credentials or passwords.

## Operational Notes

- Use a dedicated scraping account and follow X platform rules and rate limits.
- Never automate CAPTCHA or security challenge bypass.
- Do not commit screenshots, HTML dumps, cookies, or storage-state files from authenticated sessions.
- Do not include local absolute paths with personal usernames in public docs or reports.
