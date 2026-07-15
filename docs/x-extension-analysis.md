# X/Twitter Extension Analysis

## Relevant files reviewed

- `manifest.json`: grants host access to `x.com` and `twitter.com`, loads `scrapers/twitter.js` through the common content-script stack.
- `scrapers/twitter.js`: contains the X/Twitter search, latest-mode, advanced-search, tweet parsing, promoted filtering, and scrolling logic.
- `scrapers/base.js`: provides visible element helpers, promoted/ad detection patterns, text normalization, and human-like scroll behavior.
- `core/utils.js`: provides date-limit parsing helpers, canonical post key logic for Twitter status URLs, text normalization, and timestamp formatting.
- `core/navigation.js`, `content.js`, `background.js`: extension-specific messaging and UI/browser-tab control, not portable to backend.
- `tests/core.test.js`, `tests/static-check.js`: verify current extension expectations for X search URL/date-limit behavior.

## Useful behavior to reuse conceptually

- Prefer direct search URLs instead of brittle UI search when possible:
  `https://x.com/search?f=live&q={encodedQuery}&src=typed_query`.
- For date-limited searches, append `since:yyyy-MM-dd` to the query. Backend should also add `until:yyyy-MM-dd` for bounded ranges.
- Prefer Latest mode for date range scraping. The extension confirms Latest by URL `f=live` or a selected Latest tab.
- Stable tweet root selector: `article[data-testid="tweet"]`.
- Stable timestamp selector: `time[datetime]`, with the canonical status link found from the closest `a[href*="/status/"]`.
- Main text selector: `[data-testid="tweetText"]`.
- Author area selector: `[data-testid="User-Name"]`; canonical username is more safely parsed from `/username/status/{id}`.
- Image selector: `[data-testid="tweetPhoto"] img`; avatars, emojis, and `blob:` URLs must be excluded.
- Video detection can use `video`; backend should avoid storing `blob:` video URLs and may store a poster image only when it is stable.
- Promoted posts are detected by text/aria/data-testid signals such as promoted, sponsored, advertisement, and localized equivalents.
- X timeline is virtualized; collect visible posts on every scroll pass and deduplicate by status ID instead of relying on a final full DOM.

## Extension-specific parts not portable to backend

- `chrome.runtime`, `chrome.storage`, `chrome.tabs`, background-tab extraction, popup state, and content-script messaging.
- Manual username/password login flow from extension UI. Backend must use optional Playwright storage state instead.
- Direct content-script DOM event hacks for advanced-search controls. Backend should prefer direct search URLs and semantic Playwright waits.
- Extension cancellation tokens and local IndexedDB/outbox logic.

## Potentially stale or fragile areas

- UI Advanced Search modal selectors and localized labels are fragile and should not be the primary backend path.
- UI search input discovery can change; backend should use direct URL first.
- Any generated CSS class should be avoided as a primary selector.
- Video `blob:` URLs are not portable outside the browser session and must not be persisted.

## Backend implementation direction

- Implement one canonical adapter with `sourceCode = X_COM`, supporting legacy aliases `TWITTER`, `TWITTER_X`, and `X`.
- Use direct search URL generation with `since` and `until` query operators.
- Use optional Playwright storage state through a neutral browser session option, not X-specific browser engine code.
- Parse visible timeline cards incrementally across scroll attempts.
- Return `FAILED` for login wall, expired auth state, rate limiting, or a visible timeline where extraction yields no valid posts.
- Return `EMPTY` only when search genuinely has no timeline posts for the given keyword/date range.
