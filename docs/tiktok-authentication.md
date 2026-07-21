# TikTok Authentication

## Anonymous Mode

```yaml
scraper:
  tiktok:
    authentication-mode: ANONYMOUS
    authentication-required: false
```

TikTok anonymous access can be unstable. Search may work in one region/session and show a login wall, CAPTCHA, or verification in another. CAPTCHA and verification are never bypassed; the job fails with a clear TikTok error code.

## AUTO Mode

```yaml
scraper:
  tiktok:
    authentication-mode: AUTO
    authentication-required: false
    auth-state-path: ${TIKTOK_AUTH_STATE_PATH:}
    allow-anonymous-fallback: true
```

AUTO tries a configured storage state first. If no state is configured, it tries anonymous access. If a configured state is expired and anonymous fallback is allowed, it tries anonymous access. Explicit authenticated mode does not silently downgrade to anonymous.

## Create Authenticated State

```powershell
$env:TIKTOK_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\tiktok-storage-state.json"
.\gradlew.bat tiktokAuthStateInteractive
```

The task opens headed Chromium. Log in manually through TikTok UI, including QR login, CAPTCHA, 2FA, or verification if TikTok asks. The task saves storage state only after `AUTHENTICATED` and verifies it in a new BrowserContext.

Do not print or attach the storage-state JSON.

## Run Application

```powershell
$env:TIKTOK_AUTH_STATE_PATH = "$env:USERPROFILE\.java-web-scraper\auth\tiktok-storage-state.json"
.\gradlew.bat bootRun
```

If jobs return `TIKTOK_AUTH_STATE_EXPIRED`, `TIKTOK_LOGIN_REQUIRED`, or `TIKTOK_VERIFICATION_REQUIRED`, rerun `tiktokAuthStateInteractive`.

## Security

The state file contains sensitive cookies. Do not share it, commit it, attach it to bug reports, or print it with `Get-Content`. If it leaks, terminate active TikTok sessions and create a new state.
