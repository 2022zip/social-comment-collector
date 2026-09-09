# T0001: New Android Project Bootstrap

## Authorized Scope

Start from main at f78918aa087691f577ab30e466f7756902eec031 in
2022zip/social-comment-collector. The previous uncommitted attempt was discarded.
All new implementation belongs to ticket/T0001-bootstrap. Keep main unchanged.

Build a Kotlin-only Views/XML home screen, a blank secured WebView, Room schema,
ViewModel and repository infrastructure, Chinese/English resources, and tests.
No platform resolver, login flow, collection, pagination, Markdown export, or T0002.
No previous project's source, build configuration, or artifacts may be reused.

## Architecture And Runtime

MainActivity owns views and the WebView lifecycle. MainViewModel owns input and
observed task state. CollectionRepository delegates basic storage operations to
DAOs. An Application container owns the lazy Room database and repository.
UI does not access Room; ViewModel does not control cookies. Placeholder commands
do not create fake collection results or claim that login state was cleared.

Pinned baseline: JDK 17, Gradle Wrapper 8.9, AGP 8.7.3, Kotlin 2.0.21,
KSP 2.0.21-1.0.27, Room 2.6.1, compile/target SDK 35, minimum SDK 26.
Namespace/application ID: com.socialcommentcollector.app.

## Build Authority

GitHub is the only code Source of Truth. GitHub Actions is the authoritative
build and test environment. Codex local Android dependency resolution is not
an acceptance prerequisite: its managed proxy and direct DNS paths are blocked.
The observed environment failure does not establish toolchain incompatibility.

## Execution And Verification

- Commit the fresh Gradle/Wrapper/app foundation first.
- Add UI, data infrastructure, localization and focused tests in a second commit.
- Add CI and README documentation in a third commit; push only the ticket branch.
- CI must run ./gradlew --version, ./gradlew test and ./gradlew assembleDebug.
- CI must upload app/build/outputs/apk/debug/app-debug.apk and test reports.
- Compile instrumented tests in CI; running them requires a separate device gate.
- Diagnose actual CI errors, commit scoped corrections, and verify the latest SHA.
- Only a green CI run with tests, debug assembly and APK artifact proves
  T0001 Implementation Complete. It does not prove physical device behavior.

## Evidence

Reset: local clean main and remote main both matched the required SHA with only
.gitignore and README.md. No remote ticket branch existed at reset.
First authoritative CI: run 34329183619 at a3d363a4013b314d68c6c8078b2c421aa2a8dbc3.
Toolchain resolution succeeded; unit test compilation failed on missing production
contracts. Production skeleton work now supplies those contracts without removing
or weakening the existing tests.

CollectionTaskDao retains the existing insert/observeAll/deleteById contract.
CommentDao adds insert/insertAll/getByTaskId/countByTaskId. Displayed counts are
nullable; the draft factory preserves the existing zero-count contract. Device
tests cover null round trips, task and comment persistence, sorting and cascade
deletion, Activity creation, input clearing and recreation, and WebView settings.
Room is version 1 with no migration. Web navigation is HTTPS-only as a security
boundary, without platform recognition; JavaScript and DOM storage remain disabled.
Application locale preferences are separate from website login/session state.
No cookies are inspected, cleared, or managed by application business code.

Production skeleton acceptance follows the Android CI run for the published SHA;
the run must complete unit tests, debug assembly, APK upload, and device test compilation.
Manual Device Verification Pending.
