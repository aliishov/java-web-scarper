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

Interactive Gradle utilities remain available:

```shell
bash gradlew xAuthStateInteractive
bash gradlew tiktokAuthStateInteractive
bash gradlew instagramAuthStateInteractive
bash gradlew facebookAuthStateInteractive
bash gradlew threadsAuthStateInteractive
```
