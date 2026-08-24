# Social authentication bootstrap

Social credentials and Playwright storage-state files are local secrets. Copy
`local/auth/social-auth.local.example.properties` to
`local/auth/social-auth.local.properties`, fill only the platforms used locally,
and never commit the resulting file or JSON state files.

At `ApplicationReadyEvent`, the application checks each enabled platform state
in `${SOCIAL_AUTH_STATE_DIR:./local/auth}`. A valid state is reused. Missing,
empty, expired, or logged-out state triggers the platform login handler.
CAPTCHA, 2FA, checkpoints, and verification challenges are never bypassed; the
platform status becomes `CHALLENGE_REQUIRED`. Missing credentials produce
`AUTH_REQUIRED`. Passwords and storage-state contents are not logged.

Set `SOCIAL_AUTH_BOOTSTRAP_ENABLED=false` to disable startup bootstrap. With
`SOCIAL_AUTH_FAIL_STARTUP=false` (default), one failed platform does not stop
the application or news jobs. Existing social adapters still reject required
missing/expired states with an explicit failed scraper result. Set
`SOCIAL_AUTH_FAIL_STARTUP=true` when deployment must fail unless all required
platform sessions are healthy.

## Admin UI connection API

An authenticated `ADMIN` or `SUPER_ADMIN` can manage sessions through the
protected `/api/social-auth` API. The Connect dialog should call:

```http
POST /api/social-auth/{platform}/connect
Authorization: Bearer <access-token>
Content-Type: application/json

{"email":"account@example.com","password":"account-password"}
```

The request body is optional. Existing UI clients may still send `email`,
`username`, or `login` and `password`, but the visible browser is the source of
truth: the admin completes login there and no password is persisted. A
successful result has status `VALID`; `CHALLENGE_REQUIRED` means the social
network requested CAPTCHA, 2FA, or another verification step, which the
application intentionally does not bypass.

The Connect action opens a separate visible browser. Scraper jobs keep using
the normal background browser configured by `SCRAPER_BROWSER_HEADLESS` (which
should remain `true`). The Connect request remains open for up to
`${SOCIAL_AUTH_INTERACTIVE_LOGIN_TIMEOUT_MS:300000}` while the admin finishes
the verification in the opened browser. A candidate session is validated before
it replaces the current saved session, so a failed login cannot overwrite a
working connection.

Interactive Gradle utilities remain available:

```shell
bash gradlew xAuthStateInteractive
bash gradlew tiktokAuthStateInteractive
bash gradlew instagramAuthStateInteractive
bash gradlew facebookAuthStateInteractive
bash gradlew threadsAuthStateInteractive
```
