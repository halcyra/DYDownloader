# TikTok Support Design

Date: 2026-03-17
Project: DYDownloader
Scope: Add TikTok single-work, account, and collection extraction while keeping the existing shared UI and Douyin behavior intact.

## Summary

DYDownloader currently supports Douyin-only share-link recognition, cookie management, metadata extraction, and downloading. This design extends the app to support TikTok links with the same shared `Home`, `Resource`, and `Downloads` UI.

The implementation keeps platform-specific behavior in the request and cookie layers, while reusing the existing resource presentation, selection, download queue, and local persistence flow.

## Goals

- Support automatic recognition of TikTok share text and direct links from the existing main entry points.
- Support TikTok single-work, account, and collection extraction.
- Keep `Home`, `Resource`, and `Downloads` as shared cross-platform UI.
- Add a `Cookies` secondary page so the user can separately manage Douyin and TikTok cookies.
- Use `https://www.tiktok.com/login` for TikTok login.
- Keep TikTok WebView on the default mobile user agent.
- Allow TikTok login flows to jump to external apps such as Google or LINE when required.
- Preserve existing Douyin behavior without regression.

## Non-Goals

- No platform-specific tab split in the main UI.
- No live-stream support changes for this scope.
- No attempt to unify Douyin and TikTok into one downloader class.
- No broad refactor that renames all Douyin-derived model names if the current types can be reused safely.

## User Experience

### Main Entry

- The clipboard prompt and manual input dialog continue to accept share text or links from one shared entry point.
- The app detects whether the first supported URL belongs to Douyin or TikTok.
- If the text does not contain a supported Douyin or TikTok link, the app shows a generic unsupported-link error instead of a Douyin-only error.

### Shared Resource Flow

- The user pastes or opens share text from either platform.
- The app opens the same `ResourceActivity` and `ResourceFragment`.
- The platform is resolved in the background, and the corresponding downloader is used.
- Loaded resources are displayed in the existing shared list UI.

### Cookies Flow

- `Settings -> Cookies` opens a new secondary page.
- The secondary page shows two entries: `Douyin` and `TikTok`.
- Each entry displays the current status:
  - Unset
  - Request-only
  - Logged in
- Selecting `Douyin` opens the existing Douyin cookie WebView flow.
- Selecting `TikTok` opens a TikTok-specific login WebView flow at `https://www.tiktok.com/login`.

### TikTok Login Behavior

- TikTok WebView must keep the default mobile user agent.
- External-app login handoff must be allowed for links that require system handling.
- When the user leaves the screen, the app attempts to save TikTok cookies from `https://www.tiktok.com/` or the current TikTok URL.

## Architecture

## Platform Resolution

Introduce a lightweight platform-aware link resolver before metadata loading begins.

### New Types

- `Platform`
  - `DOUYIN`
  - `TIKTOK`
- `LinkKind`
  - `WORK`
  - `ACCOUNT`
  - `MIX`
  - `UNKNOWN`

### New Resolver

Add `ShareLinkResolver` with responsibilities:

- Extract the first URL from pasted share text.
- Validate that the URL belongs to a supported platform.
- Resolve the platform from the host.
- Infer a `LinkKind` hint from URL path patterns.

Supported TikTok hosts should include at least:

- `tiktok.com`
- `www.tiktok.com`
- `m.tiktok.com`
- `vm.tiktok.com`
- `vt.tiktok.com`

This resolver is used by:

- `MainActivity` for clipboard and manual input validation
- `ResourceFragment` to choose the correct downloader and cookie source

## Downloader Boundaries

Keep platform logic separate.

- Douyin remains in the existing `douyin` package.
- TikTok gets a new `tiktok` package with a dedicated `TikTokDownloader`.
- Both downloaders expose a near-identical public contract:
  - `containsXxxLink`
  - `isTrustedShareUrl`
  - `isAccountLink`
  - `isMixLink`
  - `collectWorkInfo`
  - `collectAccountWorksInfo`
  - `collectMixWorksInfo`

Both downloaders map responses into the same UI-facing resource model so `ResourceFragment`, selection logic, and queueing stay shared.

## TikTok Request Flow

TikTok must not reuse Douyin request parameter generation.

### Share Resolution

The TikTok flow mirrors the Douyin share-link resolution pattern:

- Extract the first URL from share text.
- Resolve short links such as `vm.tiktok.com` and `vt.tiktok.com`.
- Use the resolved URL and, where needed, fetched HTML to extract:
  - `itemId`
  - `secUid`
  - `collectionId`

### API Endpoints

Based on the local reference implementation, use these endpoints first:

- Single work: `https://www.tiktok.com/api/item/detail/`
- Account posts: `https://www.tiktok.com/api/post/item_list/`
- Collection posts: `https://www.tiktok.com/api/collection/item_list/`

### TikTok Parameters and Signatures

TikTok maintains a separate parameter builder from Douyin.

Required request context includes:

- `msToken`
- `device_id`
- `X-Bogus`
- `X-Gnarly`

`device_id` is loaded lazily:

- If missing locally, request `https://www.tiktok.com/explore`
- Extract `wid`
- Merge any newly obtained TikTok cookie values into the locally stored TikTok cookie

TikTok request headers and query parameters stay isolated from Douyin request logic.

## Shared UI Integration

## MainActivity

Update `MainActivity` to validate against supported Douyin or TikTok links instead of Douyin-only links.

Impacts:

- Clipboard prompt auto-detection
- Paste dialog validation
- Error strings

## ResourceFragment

`ResourceFragment` remains shared, but becomes platform-aware when loading content.

High-level flow:

1. Resolve platform and link kind from the input share text.
2. Load the correct cookie for that platform.
3. Construct the matching downloader.
4. Run the existing concurrent probing approach within that platform only.

Behavioral notes:

- Do not probe Douyin and TikTok in parallel.
- If the resolver provides a strong `LinkKind` hint, prefer that probe path first.
- If the hint is weak, keep the current `work/account/mix` concurrent probing pattern for the resolved platform.

## Cookies Secondary Page

Add a new `CookiesActivity` between settings and the login WebView.

Responsibilities:

- Present `Douyin` and `TikTok` entries
- Show per-platform cookie status
- Launch `CookieWebViewActivity` with an explicit target platform

`SettingsActivity` remains the top-level owner of the `Cookies` entry.

## CookieWebViewActivity

Parameterize the activity by platform.

### Douyin Mode

- Keep the existing Douyin desktop-UA behavior.
- Keep the existing login target.

### TikTok Mode

- Open `https://www.tiktok.com/login`
- Do not override the WebView user agent with the current desktop UA
- Allow external-app login handoff

External handling should cover:

- `intent:` URIs
- non-HTTP(S) schemes
- supported login redirects that the WebView cannot or should not complete internally

When the activity finishes:

- Attempt to read cookies from `https://www.tiktok.com/`
- If empty, fall back to the current TikTok URL when it is trusted

## Data Model and Persistence

The UI stays shared, but stored data must become explicitly platform-aware.

## Platform Enum

Add a new `Platform` enum with at least:

- `DOUYIN`
- `TIKTOK`

## ResourceItem

Add `platform` to `ResourceItem`.

Rationale:

- The main UI is shared
- The queue is shared
- The same numeric resource ID can exist on both platforms
- Platform must survive process death and task restoration

## AwemeProfile Reuse

Reuse the current `AwemeProfile` type but add `platform`.

Rationale:

- It already behaves like a generic downloadable-work model
- Renaming it to a neutral type would create broad churn without providing direct value for this scope

## Database Changes

Increase Room schema version from `5` to `6`.

Add `platform TEXT NOT NULL DEFAULT 'DOUYIN'` to:

- `resources`
- `download_tasks`

Migration behavior:

- Existing persisted data is treated as Douyin
- No complex backfill is needed

## Keying Strategy

Do not prefix `sourceKey` itself with platform.

Keep `sourceKey` platform-local, for example:

- Douyin: `7363189720901351234#photo:2`
- TikTok: `7345678901234567890#photo:2`

Instead, make the cross-platform unique key explicit at the item/task level:

- `ResourceItem.key()` returns `platform + ":" + sourceKey`

Rationale:

- Existing `SourceKeyUtils` logic assumes a clean resource key
- Many current paths parse `#photo:`, `#live:`, and `#cover`
- Keeping `sourceKey` clean reduces required changes in queueing and payload generation logic

## DAO Changes

Add platform-aware lookup methods:

- `getBySourceKey(platform, sourceKey)`
- `getByParentIdAndSourceKey(parentId, platform, sourceKey)`

Existing platform-agnostic `sourceKey` lookups must be replaced to avoid cross-platform collisions.

## Download Pipeline

## Cookie Selection

`DownloadManager` must choose cookies by `item.platform()` rather than using one global cookie.

## Downloader Selection

`DownloadPayloadFactory` dispatches by platform:

- `DOUYIN -> DouyinDownloader`
- `TIKTOK -> TikTokDownloader`

## HTTP Request Context

`HttpDownloader` should no longer depend on `DouyinDownloader.shouldAttachCookie(url)`.

Introduce a small request context abstraction containing:

- `userAgent`
- `referer`
- `cookie`
- allowed cookie hosts

Douyin and TikTok each provide their own download request context.

This keeps shared file download code while isolating platform-specific headers and cookie rules.

## Testing Strategy

Implementation follows a test-first approach.

### First Wave

- `ShareLinkResolverTest`
  - Detects Douyin and TikTok links from share text
  - Accepts TikTok short-link hosts
  - Produces correct `Platform` and `LinkKind`
- `AppPrefsTest`
  - Douyin/TikTok cookies are stored separately
  - Per-platform request-only and logged-in states do not interfere
- `SourceKeyUtilsTest`
  - Continues to operate on clean platform-local source keys
- `DownloadQueueTest`
  - Cross-platform keys do not collide
  - Base-key matching remains correct within a platform
- `TikTokDownloaderTest`
  - Trusted host validation
  - TikTok link detection in share text
  - Extraction of `itemId`, `secUid`, and `collectionId` from URL or HTML snippets

### Second Wave

- `DownloadPayloadFactoryTest`
  - Dispatches by platform
  - Correctly infers cover URLs and media types for TikTok items

### Manual Verification

Because the project does not currently include WebView UI automation, validate manually:

- `Settings -> Cookies -> TikTok` navigation works
- `https://www.tiktok.com/login` loads
- Google/LINE external app redirects do not crash the app
- TikTok cookies are saved when returning
- TikTok single work, account, and collection links load through the shared UI
- Existing Douyin single work, account, and collection flows still work
- Download queue behavior remains stable across process restart

## Delivery Order

1. Add `Platform`, dual-cookie storage, and Room migration.
2. Add `ShareLinkResolver` and update `MainActivity` validation.
3. Add `CookiesActivity` and parameterized `CookieWebViewActivity`.
4. Implement TikTok single-work extraction first.
5. Implement TikTok account and collection extraction.
6. Update download pipeline to select request context and cookie by platform.
7. Update strings and README for dual-platform support.

## Risks

- TikTok web parameters and signatures are more volatile than Douyin.
- External-app login success depends on installed apps and OS handoff behavior.
- Shared UI is low risk, but only if storage, cookies, and request context stay explicitly platform-aware.

## Acceptance Criteria

- TikTok share text opens through the existing shared entry flow.
- TikTok single work, account, and collection resources can be loaded in the shared `Resource` UI.
- Douyin flows still work as before.
- The user can manage Douyin and TikTok cookies independently from a `Cookies` secondary page.
- TikTok login uses `https://www.tiktok.com/login`, keeps the default mobile user agent, and allows external-app handoff.
- Persisted resources and queued downloads do not collide across platforms.
