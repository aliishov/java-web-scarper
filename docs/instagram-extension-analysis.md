# Instagram extension analysis

## Scope

This document records the Instagram-specific behavior found in the existing Chrome extension at:

`C:\Users\Admin\Desktop\multi_platform_scarper_tt_new`

The extension was used only as a technical reference. No files in that project were changed.

## Files reviewed

- `manifest.json`
- `popup.js`
- `core/navigation.js`
- `core/utils.js`
- `scrapers/base.js`
- `scrapers/instagram.js`
- `tests/core.test.js`
- `tests/static-check.js`

## Current extension flow

The extension registers `scrapers/instagram.js` for Instagram pages. Its active strategy is direct navigation to:

`https://www.instagram.com/explore/search/keyword/?q={encodedKeyword}`

The scraper confirms that the current URL still matches the normalized keyword, then attempts to select a Fresh/Recent UI control when present. If no freshness control is visible, the extension logs that posts will be buffered and sorted by publication date.

The collection flow is:

1. Gather grid links from `a[href^="/p/"]` and `a[href^="/reel/"]`.
2. Normalize each candidate URL by removing query and fragment data.
3. Keep candidates unique by URL.
4. Scroll the grid until enough candidates are discovered or the grid stops changing.
5. Open each candidate in an Instagram modal.
6. Wait for date, author identity, or media evidence.
7. Expand caption text with a scoped More button.
8. Collect carousel media by clicking scoped Next buttons.
9. Parse author, date, caption, URL, and media.
10. Close the modal and continue.

## Search strategy

Useful extension behavior:

- It avoids relying on Enter inside the search input because that path was previously unreliable.
- It uses direct search URL navigation as the primary behavior.
- It has a lightweight check that the search URL query matches the keyword.
- It attempts a Fresh/Recent control but tolerates its absence.

Backend implementation should keep the layered idea:

- Use authenticated storage state.
- Try a semantic UI search only when live DOM confirms it.
- Fall back to a direct Instagram search URL.
- Support hashtag pages separately from arbitrary keyword search.
- Do not pretend Instagram provides a full caption search when live behavior does not prove it.

## Selectors and URL patterns

Useful selectors from the extension:

- Grid links:
  - `a[href^="/p/"]`
  - `a[href^="/reel/"]`
- Post modal/root:
  - `div[role="dialog"] article`
  - `div[role="dialog"]`
- Post readiness:
  - `time[datetime]`
  - `time[title]`
  - profile links inside the container
  - `img[src]`
  - `video`
- Profile links:
  - `header a[href]`
  - `a[role="link"][href]`
- Caption candidates:
  - `h1[dir="auto"]`
  - `[data-testid="post-comment-root"] span[dir="auto"]`
  - `ul li span[dir="auto"]`
  - `article span[dir="auto"]`
- Carousel next:
  - scoped `button[aria-label="Next"]`
  - localized scoped next buttons where available
- Close modal:
  - scoped Close buttons or Escape fallback

Backend code should prefer semantic selectors, URL patterns, `time[datetime]`, `article`, `dialog`, roles, ARIA labels, and href patterns. Obfuscated class names must not be primary selectors.

## URL handling

The extension previously used the full candidate URL as post identity. Backend code must be stricter:

- `/p/{shortcode}/` is a post candidate.
- `/reel/{shortcode}/` is skipped unless reels are enabled.
- `/tv/{shortcode}/` is parsed as its own post type if encountered.
- Query parameters, fragments, `igsh`, and `utm_*` parameters are removed.
- `externalPostId` must be the shortcode.
- `postUrl` must be the canonical URL.

Correct backend shape:

`externalPostId = shortcode`

`postUrl = https://www.instagram.com/p/{shortcode}/`

## Extraction logic

The extension uses scoped modal extraction. For backend stability, direct canonical post page extraction is preferable when possible because it avoids stale modal content and preserves search-grid scroll state.

Author extraction should use the first main post author profile link:

- username from `/{username}/`;
- profile URL as `https://www.instagram.com/{username}/`;
- external author ID as lowercase username when a numeric ID is not available;
- avatar only from the post header, not comments or suggestions.

Caption extraction must stay scoped to the post author's caption block. It must not use `body.textContent()` or the whole article text. Comments, likes, replies, engagement text, dates, related posts, and accessibility labels should be excluded.

Media extraction must stay scoped to current post media:

- collect HTTPS image/video/poster URLs;
- ignore avatars, emoji, icons, `data:`, and `blob:`;
- traverse carousel with a scoped Next button;
- avoid global next-post navigation.

## Scrolling logic

The extension already accounts for a virtualized DOM by collecting currently visible links before each scroll. Backend implementation should preserve that concept:

- use `LinkedHashMap` keyed by shortcode or canonical URL;
- track duplicate candidates;
- track scroll attempts and consecutive no-new iterations;
- stop on max posts, max candidates, max scroll attempts, no-new limit, auth failure, challenge, or rate limit;
- avoid early date stop unless recent ordering has been confirmed.

## Known extension issues

- Enter inside search was unreliable.
- Direct search URL behavior still needs live verification.
- Candidate identity was URL-based and could duplicate if query/fragment data changed.
- `/p/` and `/reel/` were collected together; backend should skip reels by default.
- Modal extraction can accidentally parse stale or neighboring content if the modal does not update fully.
- Caption extraction by longest text node can pick a comment on some layouts.
- Media extraction can pick thumbnails or avatars unless strongly scoped.
- Date parsing can return null when `time[datetime]` is absent.
- The extension opens candidates in the same page, so returning to the first card or losing scroll position is possible.

## Conceptual reuse in backend

The backend should reuse:

- authenticated Playwright storage state;
- direct search URL fallback;
- semantic grid link discovery;
- canonical shortcode-based deduplication;
- scoped post root extraction;
- scoped caption expansion;
- scoped carousel traversal;
- diagnostics counters;
- local filtering by `postDate`.

The backend should not port:

- credential-based login;
- CAPTCHA, 2FA, checkpoint, rate-limit, or privacy bypasses;
- extension-specific synthetic input machinery as the default strategy;
- whole-page text/media extraction;
- private API calls or stolen tokens;
- screenshots, HTML dumps, cookies, or storage state in Git.

## Live checks still required

With a valid `INSTAGRAM_AUTH_STATE_PATH`, live QA should confirm:

- authenticated page detection;
- current search UI behavior;
- direct search URL behavior;
- hashtag page behavior;
- result types returned by Instagram;
- post grid selectors;
- Recent/Top section availability;
- direct post page extraction;
- author, caption, date, media, carousel, sponsored, and reel detection;
- scrolling stop conditions;
- data quality after ingestion.
