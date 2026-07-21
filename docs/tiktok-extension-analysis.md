# TikTok Extension Analysis

## Studied Files

- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\manifest.json`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\popup.html`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\popup.js`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\core\navigation.js`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\core\state-machine.js`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\scrapers\tiktok.js`
- `C:\Users\Admin\Desktop\multi_platform_scarper_tt_new\tests\static-check.js`

## Existing Flow

The extension registers `scrapers/tiktok.js` through `manifest.json` and starts it as the `tiktok` platform from the popup. The scraper searches through the UI first, falls back to `https://www.tiktok.com/search/video?q={keyword}`, tries to confirm the Videos tab, collects canonical `/@username/video/{videoId}` links, opens the viewer, then extracts caption/media/author/date.

## Useful Ideas Reused

- Canonical post identity is `/@username/video/{videoId}` and `videoId` is the external post ID.
- Candidate collection deduplicates by `videoId`, not DOM card count.
- Search card captions are unreliable; detail page/root extraction is preferred.
- Video `blob:` URLs must not be stored; HTTPS `source[src]` or poster is acceptable.
- Blocker states such as CAPTCHA/login/rate-limit must be diagnostics/failure states.

## Root Cause: ensureReady Error

The old readiness concept could depend on a narrow UI selector or route confirmation. TikTok can expose hydrated search results without the expected search input, can show cookie/login/challenge states, and may keep network requests alive. The backend verifier now treats readiness as a page state with multiple semantic markers and blocker detection.

## Root Cause: text empty

Caption text is often absent or truncated on search cards. The extension fixed this by opening detail/viewer pages and checking scoped caption selectors plus metadata fallback. The backend uses the same concept without copying the extension code directly.

## Selectors Treated As Useful But Volatile

- `a[href*="/video/"]`
- `[data-e2e*="browse-video-desc"]`
- `[data-e2e*="video-desc"]`
- `[data-e2e*="search-card-video-caption"]`
- `[data-e2e*="browse-username"]`
- `video`, `source[src]`, `video[poster]`

## Deliberately Not Used

- Private API endpoints.
- Request signatures or anti-bot parameters.
- CAPTCHA/login challenge bypass.
- Whole-page text extraction as post caption.
- Comment/like/follower/private data extraction.
