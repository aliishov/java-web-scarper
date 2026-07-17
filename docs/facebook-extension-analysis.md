# Facebook extension analysis

## Scope

This document records the Facebook-specific behavior found in the existing Chrome extension at:

`C:\Users\Admin\Desktop\multi_platform_scarper_tt_new`

The extension was used as a reference only. No files in that project were changed.

## Files reviewed

- `manifest.json`
- `popup.js`
- `core/navigation.js`
- `scrapers/facebook.js`
- `tests/core.test.js`
- `tests/static-check.js`

## Current extension flow

The extension registers `scrapers/facebook.js` for `facebook.com` pages and starts from the regular Facebook UI. `popup.js` maps the platform to `https://www.facebook.com/`. The scraper first checks whether the user is on a login page, then uses the search UI when available. The navigation helper focuses the search input, types the keyword, presses Enter, verifies that navigation happened, and then falls back to more direct techniques when UI confirmation is weak.

For backend usage, the relevant idea is not the low-level browser event simulation itself. The reusable concept is a layered search flow:

1. Open Facebook home with an authenticated session.
2. Try semantic search input selectors.
3. Press Enter and wait for results.
4. Fall back to a direct search URL.
5. Collect visible post containers while scrolling a virtualized feed.

## Useful selectors and DOM ideas

The extension uses these selectors and DOM signals that are useful for the backend adapter:

- Search inputs:
  - `input[type="search"]`
  - `input[name="q"]`
  - `[role="search"] input`
  - `input[placeholder*="Search" i]`
  - `input[aria-label*="Search" i]`
  - `[role="combobox"][contenteditable="true"]`
  - `[role="textbox"][contenteditable="true"]`
- Post containers:
  - `article`
  - `[role="article"]`
  - `[role="feed"] > div`
  - `[data-pagelet*="FeedUnit"]`
  - ancestor containers around permalink or time links
- Message nodes:
  - `[data-ad-rendering-role="story_message"]`
  - `[data-ad-comet-preview="message"]`
  - `[data-ad-preview="message"]`
  - `[data-testid="post_message"]`
- Author/profile block:
  - `[data-ad-rendering-role="profile_name"]`
  - scoped `a[role="link"][href]`
- Media:
  - `[data-ad-rendering-role="image"]`
  - post-scoped `img[src]`
  - post-scoped `video`
- Permalinks:
  - `/posts/`
  - `/permalink/`
  - `story_fbid=`
  - `fbid=`
  - `/groups/.../posts/...`
  - `/reel/`
  - `/videos/`

The backend implementation should prefer semantic selectors and scoped parsing over class names, because Facebook class names are volatile.

## Reusable extraction ideas

The extension scores candidate containers instead of trusting every `article`. A complete post container needs:

- an author block;
- date/timestamp evidence;
- a permalink;
- text or media;
- not the whole feed container.

The backend adapter should keep this idea. It should reject large feed wrappers, media-only wrappers, navigation cards, suggestions, and containers without a canonical post identity.

The extension also keeps DOM interaction scoped. Text expansion is attempted only inside the selected post message area, not through global "More" buttons. Media extraction skips avatars, icons, emoji, `data:` URLs, and `blob:` URLs.

## Date handling

The extension date parser supports:

- Unix timestamps;
- ISO-like timestamps;
- `Just now`, `Today`, `Yesterday`;
- relative English, Russian, and Azerbaijani units;
- absolute English, Russian, and Azerbaijani month names;
- `data-utime`, `time[datetime]`, `abbr[title]`, and timestamp link labels.

Important lesson: media accessibility tooltips can look text-like but are not publication dates. Date extraction must avoid image/video accessibility labels.

## URL handling

The extension normalizes Facebook URLs and strips tracking parameters while preserving post identity. Backend support should preserve:

- `story_fbid`;
- `id`;
- `fbid`;
- `v`;
- `/posts/{id}`;
- `/groups/{group}/posts/{post}`;
- `/reel/{id}` when reels are enabled.

The backend should strip tracking parameters such as `__cft__`, `__tn__`, `mibextid`, `ref`, `refid`, and `locale`.

## Known extension issues

- It relies on live Facebook DOM behavior that can change without warning.
- Some strings and selectors are locale-dependent.
- The extension uses low-level DOM events and focus/caret checks that are browser-extension specific.
- Hover-based timestamp extraction is fragile and can open informational popups.
- Virtualized feeds can remove nodes while scrolling, so deduplication must be based on canonical post identity.
- Global `article` selection is too broad unless combined with scoring and identity checks.
- "More" buttons outside the message area can open menus or content-info dialogs instead of expanding text.

## Obsolete or risky selectors

These should not be used as the only source of truth in the backend:

- generic `article` without scoring;
- obfuscated class names;
- global `div[dir="auto"]` text extraction;
- global `img[src]` media extraction;
- any unscoped button text like `More`;
- media tooltip text as publication date evidence.

## Conceptual reuse in backend

The backend will reuse these concepts:

- storage-state authenticated browser session;
- layered search flow with direct URL fallback;
- semantic selectors and scoped container parsing;
- canonical URL/external ID deduplication;
- localized date parsing with an injected clock;
- scoped "See more" handling;
- media filtering that excludes avatars/icons/data/blob URLs;
- diagnostics counters without logging private data or full post text.

The backend will not port these extension-only mechanisms:

- automated login with credentials;
- CAPTCHA, checkpoint, or 2FA handling;
- full extension navigation state machine;
- synthetic DOM keyboard/mouse events as a default strategy;
- storage of cookies or account details in repository files.

## Live DOM checks still required

The implementation can compile and run without a Facebook storage state, but live scraping requires a Playwright storage state created manually by the operator. When a valid storage state is available, the following must be checked manually:

- authenticated home page detection;
- search input selector;
- direct search URL behavior;
- Posts filter confirmation;
- Recent filter confirmation;
- post container boundaries;
- permalink extraction;
- author extraction;
- text expansion;
- publication date extraction;
- media extraction;
- sponsored and reel skipping;
- scrolling stop conditions.
