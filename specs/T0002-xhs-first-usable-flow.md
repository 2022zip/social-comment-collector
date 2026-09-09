# T0002 — Xiaohongshu First Usable Collection Flow

## Goal

Build the first end-to-end collection flow for one Xiaohongshu URL:

`URL input → platform detection → WebView/session → content and comment collection → incremental Room persistence → terminal status → Markdown → history → Android Share Sheet`

Jike recognition is included only in the URL resolver. Jike collection is outside this ticket.

## Source of Truth and Delivery

- Branch: `ticket/T0002-xhs-first-usable-flow`, starting at `df73b0f7b7dead2783ba725c4edbd49f38249f63`.
- `main` remains unchanged.
- Every checkpoint is committed, pushed, and verified by GitHub Actions before the next checkpoint starts.
- GitHub Actions is authoritative for dependency resolution, JVM tests, Android compilation, `assembleDebug`, and APK publication.
- Real Xiaohongshu page compatibility remains unverified until the later device-verification stage.

## Fixed Technical Baseline

- Kotlin-only, Android Views + XML.
- JDK 17, Gradle Wrapper 8.9, AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.27.
- compileSdk 35, targetSdk 35, minSdk 26.
- Room remains the durable source of truth.
- No credentials, usernames, or passwords are stored by the app.

## Architecture

The UI forwards user intent to `MainViewModel`. Use cases own orchestration and status decisions. Android-specific WebView operations are exposed through a narrow collection host; platform selectors and parsing never live in `MainActivity`. `XiaohongshuCollector` coordinates session probes and page extraction, parsers convert structured page payloads into domain records, and the repository persists each batch transactionally.

Primary flow:

`MainActivity → MainViewModel → collection use case → XiaohongshuCollector → repository → Room`

WebView callbacks return session and collection events to the use case through explicit interfaces. Markdown generation reads only persisted Room data.

## URL Resolution

`PlatformDetector` parses a URL and matches normalized hosts, never titles or arbitrary substring text.

- Xiaohongshu: `xiaohongshu.com`, its subdomains, and `xhslink.com` plus its subdomains.
- Jike: `okjike.com` and its subdomains.
- Every other or malformed input: `Platform.UNKNOWN`.

`UrlResolver` owns HTTPS redirect resolution for short links. Redirects are bounded, reject scheme downgrade, and return the final URL plus detected platform. Activity code does not perform redirect resolution.

## WebView and Session

- JavaScript and DOM storage are enabled only because the target site requires them; file/content access and mixed content remain disabled.
- `CookieManager` accepts first-party cookies and flushes WebView-managed persistence. The app has no custom credential store.
- Session states are `UNKNOWN`, `LOGIN_REQUIRED`, `READY`, and `EXPIRED`.
- Cookie presence is only supporting evidence. Readiness requires a page probe that can distinguish login UI, usable content state, and expired/unauthorized responses.
- Login occurs manually inside the WebView. The app never fills credentials or bypasses access controls.
- Session reset requires user confirmation, clears WebView cookies and relevant WebView storage/cache, and leaves Room and exported Markdown untouched.

## Xiaohongshu Collection

The collector uses an in-page JavaScript bridge against the page the user can normally access. It first prefers structured state already present in the page. DOM extraction is a fallback. It does not call undocumented endpoints outside the WebView session or defeat platform protections.

The page bridge emits versioned payloads:

- session probe;
- content snapshot;
- comment batch;
- progress/heartbeat;
- terminal end marker;
- typed failure.

Kotlin parsers validate payload versions and required fields. Missing fields remain null; no value is invented. Initial parser tests use deterministic, sanitized contract fixtures because no current real-device page capture is available. Those fixtures do not constitute proof of current production-site compatibility.

## Pagination and Completion

Collection repeatedly expands visible replies and advances lazy loading. There is no product-level comment cap.

`COMPLETED` requires an explicit platform/page end marker and a final stable pass with no new comment identities. Repeated pages, repeated cursors, no-new-data cycles, bridge silence, timeout, WebView interruption, session expiry, parse failure, and network failure are safety stops, not completion evidence. If useful content exists they produce `INCOMPLETE`; otherwise they produce `FAILED`.

## Persistence and Deduplication

Room schema version advances to 2 with an explicit migration. A one-to-one content record stores author, body, publication time, and collection time. Comments gain a stable identity key with a unique index scoped to the task.

Identity priority:

1. platform comment ID;
2. SHA-256 composite of task, parent identity, normalized author, publication time, normalized content, and stable source position/path.

Content alone is never a deduplication key. Parent/reply relationships are retained.

Every received batch is written in a Room transaction using conflict-ignore semantics. The transaction recounts persisted comments and updates `actualSavedCommentCount`; page estimates never set that field. Previously saved batches survive interruption and retries.

## Task State Policy

- A new URL creates its own task as `QUEUED`.
- Active collection changes it to `COLLECTING`.
- `COMPLETED`: explicit no-more-comments evidence.
- `INCOMPLETE`: useful content exists but complete traversal cannot be proven.
- `FAILED`: no meaningful exportable result exists.

Invalid transitions are rejected by a tested state policy. `failureReason` is specific and localized for UI display; persisted failure codes remain stable and are not the generic word `Error`.

## Markdown and History

One task produces one Markdown file named `YYYY-MM-DD_平台_标题.md`. Filename generation sanitizes illegal characters, handles blank/long titles, and avoids collisions.

Markdown contains title, platform, original URL, author, publication time, collection time, displayed and actual comment counts, status, failure reason, body, and all persisted comments in order. Replies are indented beneath their parent. Markdown is generated from Room only after an exportable terminal state; it is not rewritten per comment.

The history screen shows one newest-first card per task with title, platform, textual status, time, counts, failure reason, and an export/share action where available. Status is never communicated by color alone.

Sharing uses `FileProvider` and `ACTION_SEND` with the generated `.md` file. The recent-share action selects the newest exportable task or reports that no result is available. Google Drive is only an Android share target; no Drive API or OAuth is included.

## Verification

JVM tests cover URL/platform detection, redirects through a fake transport, session-state interpretation, parser fixtures, pagination completion rules, status transitions, fallback identity, Markdown rendering, filename sanitization, count formatting, and ViewModel behavior.

Room instrumented tests cover migration, multi-batch inserts, deduplication, parent/reply persistence, recounting, and incomplete-task retention. UI instrumented tests cover supported/unsupported input, start behavior, history rendering, export availability, reset confirmation, and FileProvider intent creation. The first CI workflow may compile rather than execute emulator tests; non-execution is reported explicitly.

Each checkpoint must pass the existing authoritative CI commands:

- `./gradlew --version`
- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew assembleDebugAndroidTest`
- Debug APK artifact upload

## Checkpoints

1. `feat: add unified platform URL resolver`
2. `feat: add Xiaohongshu session and collection foundation`
3. `feat: persist Xiaohongshu content and comments incrementally`
4. `feat: add collection history and markdown export`
5. `feat: add markdown sharing and session reset`

## Explicit Exclusions

- Jike collection implementation.
- Any old `comment_app` source or build artifact.
- Automated credential entry, login bypass, anti-bot bypass, or access-control circumvention.
- Fixed product-level comment limits.
- Google Drive API/OAuth or automatic uploads.
- WeChat Official Account collection.
- Merge, PR, or modification of `main`.

## Completion Boundary

Implementation completion requires all five checkpoints to be pushed, authoritative GitHub Actions to pass tests and `assembleDebug`, and the debug APK artifact to exist. It proves the implementation and controlled fixture behavior, not live Xiaohongshu compatibility. The latter is reserved for First Real Device Functional Verification.
