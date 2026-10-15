# QA Engineer Take-Home Assessment

**Scope:** Offline-first Android survey application + dynamic web Survey Builder  
**Automation stack:** Playwright Test + Node.js mock API  
**Status:** Example assessment submission; replace assumptions with the actual product contract before execution.

> **Reference documents reviewed:** `Android App Specification.html`, `Survey Builder Tool Capabilities.html`, and `Sample Survey Schema.json` were supplied in `QA-Assessment (1).zip`. The browser fixture mirrors the schema's `Student Interests & Hobbies` rule (`q_4` is shown when `q_3` equals `Yes`). The API endpoint contract remains illustrative because the references do not define the backend sync API.

## Repository contents

```text
.
├── README.md
├── package.json
├── playwright.config.js
├── mock-server.js
├── fixtures/
│   └── dynamic-survey.html
└── tests/
    ├── sync-api.spec.js
    └── skip-logic.spec.js
```

## 1. Part 1 — Hands-on technical inspection & automation

### Scenario A — Offline sync and data collision

#### Contract used by this mock

`POST /sync-survey` accepts:

```json
{
  "submissionId": "sub-ankit-001",
  "deviceId": "device-ankit",
  "localId": "School_1",
  "entityName": "Govt Primary School",
  "schemaVersion": "v1",
  "answers": {"building_condition": "Good"},
  "capturedAt": "2026-10-14T10:00:00+05:30"
}
```

The server returns `201` for a newly accepted submission and `200` for a retry of an already accepted `submissionId`. The mock treats `(deviceId, localId)` as a local record identity and `submissionId` as an idempotency key. Therefore Ankit's and Pooja's `School_1` records remain distinct because they came from different devices. **This is an illustrative contract**; in production, confirm the intended identity and merge policy with product/backend engineers.

#### Important assertions

- Same `localId` from two devices: accept both, preserve both payloads, never silently overwrite.
- Same `submissionId` and same body retried: return the original result and do not create another row.
- Same `submissionId` with a different body: reject as an idempotency conflict (`409`).
- Missing required fields / malformed JSON: reject with a client error and do not persist.
- Server error or timeout: client retains the local record and retries with backoff; retry is idempotent.
- Concurrent requests: verify no lost updates or duplicate rows under parallel load. The in-memory mock demonstrates functional behavior only; it is not a load-test server or proof of database transaction safety.

#### Background sync test strategy

1. Create an offline survey and photo; record the local ID, submission ID, answer count, attachment hash/size, and local database row count.
2. Enable Charles Proxy or Proxyman on a test Wi-Fi network. Configure the Android device's proxy and install the proxy certificate only in a dedicated test environment if HTTPS inspection is required. Do not weaken production TLS.
3. Throttle to 2G-like latency/bandwidth, then test airplane mode, Wi-Fi/mobile-data switching, DNS failure, connection reset, timeout, HTTP 500, and authentication expiry.
4. For the “photo upload fails at 99%” case, use a proxy rule or a controllable test server to terminate the connection during the final chunk/response. Repeat for chunked and non-chunked upload paths if supported.
5. Check that the client marks the item as pending/retryable, never marks it synced before server confirmation, and does not lose or corrupt the photo. Compare file hash/size and survey answers before and after retries.
6. Restore connectivity. Confirm exponential backoff with jitter, bounded retry behavior, and eventual success. Verify duplicate retries do not create duplicate server records.
7. Run the scenario repeatedly and across app process death, device reboot, and app upgrade. Capture Logcat, proxy session, WorkManager state, server request IDs, and database snapshots.

**50,000 simultaneous users:** Do not simulate this by simply launching 50,000 device processes. Use a load tool such as k6 or Gatling against a staging environment with representative payload sizes and ramp-up/arrival-rate profiles. Measure p50/p95/p99 latency, error rate, queue depth, DB connection pool saturation, CPU/memory, throttling, and recovery after the spike. Add client-side randomized sync scheduling, exponential backoff with jitter, bounded concurrency, server-side rate limiting, queue-based ingestion, idempotency keys, and observability. Confirm that retries do not amplify an outage.

### Scenario B — Dynamic form rendering and regression

The included Playwright test mirrors the supplied `Student Interests & Hobbies` schema: `q_3` asks “Do you have a hobby?” and `q_4` (“What is your hobby? (If Yes)”) has `visibilityLogic.enabled=true`, `targetQuestion=q_3`, `condition=equals`, and `value=Yes`. The fixture is a small deterministic renderer for that rule, not the production Survey Builder itself.

**Reference schema defect found:** the supplied JSON is syntactically invalid at the `hfc_sleep_plausible` rule because a comma is missing between two objects in `logic`. The property is written as `questioID` rather than the documented `attr`, and the rule requires `q_2 = Yes` while `q_3` is visible only when `q_2 = No`; this AND condition is unreachable under the documented visibility rule. I would report this as a schema/data defect and ask the product owner to confirm intended semantics rather than silently changing the original file. The automated fixture focuses on the valid hobby skip rule.

For JSON-generated forms, build a schema-driven regression matrix rather than hard-coding one test per screen:

- **Schema contract tests:** validate required keys, supported question types, unique question IDs, valid references in skip rules, default values, roster definitions, and schema version compatibility.
- **Renderer smoke tests:** generate each supported question type and assert it renders, accepts input, serializes correctly, and restores correctly after reload/reopen.
- **Logic tests:** truth tables for each condition/operator; nested AND/OR; hidden required questions; boundary values; missing/null answers; circular rules; changing a parent answer after child answers have been entered.
- **Roster tests:** zero, one, typical, and large member counts; add/remove/reorder; per-member answer isolation; persistence across app restart.
- **Golden/contract tests:** a curated set of representative schemas covering common patterns and risky combinations, run on every PR.
- **Compatibility:** schema versions generated by the web builder must be consumed by supported Android app versions; test unknown optional fields and unsupported required features gracefully.
- **Offline lifecycle:** download schema, fill form offline, kill/reopen app, update schema when online, and verify draft migration or an explicit safe error.
- **Visual/accessibility checks:** automated snapshots for stable layouts plus manual checks for sunlight readability, screen-reader labels, focus order, font scaling, and touch targets.

**Automate:** repeatable logic, serialization, validation, persistence, API contracts, smoke flows, and deterministic device/network regressions.  
**Keep exploratory/manual:** first-use comprehension, low-literacy usability, confusing wording, real-world sunlight/glare, one-handed operation, unexpected navigation patterns, and nuanced accessibility judgments. Automate objective accessibility checks where possible, but do not treat them as a replacement for user observation.

### Scenario C — Room, WorkManager, and retry inspection

Exact steps depend on the release build's debuggability and package configuration. An ordinary **non-debuggable release APK usually does not allow `run-as` access** to private app data on an unrooted device. Do not promise that the private Room database can always be pulled from a release device.

#### Inspect Room data

1. Install the target APK on a dedicated test device/emulator and record app version, package name, Android version, and test account.
2. Create one survey offline. Record expected answers, attachment metadata, local ID, submission ID, and status shown by the UI.
3. Prefer a QA/debug build with `android:debuggable="true"` or a dedicated diagnostic/export feature. With a debuggable build, run:
   ```bash
   adb shell run-as com.example.survey ls databases
   adb exec-out run-as com.example.survey cat databases/survey.db > survey.db
   ```
   Replace `com.example.survey` and `survey.db` with the real package/database names. WAL mode may require copying the database and its `-wal` / `-shm` companions consistently; a raw `cat` of a live database is not always a transactionally consistent snapshot.
4. Inspect the copied database with `sqlite3` or DB Browser for SQLite:
   ```sql
   .tables
   PRAGMA integrity_check;
   SELECT * FROM survey_submissions;
   ```
   Use actual table and column names from the app. Check answers, status, IDs, timestamps, schema version, and attachment references.
5. If the release is not debuggable, use Android Studio Database Inspector only when supported by the running process/build, an approved diagnostic export, instrumented test hooks, or backend/UI evidence. Do not root a production user's phone or bypass platform protections.

#### Trigger WorkManager

WorkManager jobs are not generally force-run by a single universal ADB command. The reliable route is a QA/debug build with a test-only trigger that enqueues the same worker/request path, or an instrumented test using `WorkManagerTestInitHelper` and `TestDriver` to set constraints and advance delays. For black-box release testing:

```bash
adb shell dumpsys jobscheduler
adb logcat -c
adb logcat | grep -i -E "WorkManager|SyncWorker|survey"
```

Then restore connectivity or satisfy the worker's constraints and observe execution. `adb shell am broadcast` is not a generic way to trigger WorkManager unless the app explicitly implements a permitted receiver. Avoid undocumented production backdoors.

#### Force a 500 and prove safe retry

1. Configure Charles/Proxyman or a controllable staging endpoint to return `500` for `POST /sync-survey`, preserving the request ID and response timing.
2. Run sync and capture proxy history, Logcat, WorkManager diagnostics, and local DB state.
3. Verify the request is considered failed/retryable; local answers and attachments remain present and unchanged; the UI does not claim successful sync.
4. Verify a retry is scheduled according to the worker's backoff policy, not in a tight loop. The retry should reuse the idempotency/submission ID.
5. Change the proxy rule to allow the request, restore network, and verify eventual server acceptance, a single server-side record, and a local status transition only after success.
6. Repeat after process death and reboot. Also test permanent errors (e.g. validation 4xx) separately from transient errors (5xx/timeouts), according to the product's retry policy.

## 2. Part 2 — Complex scenarios and edge cases

### Scenario D — ANR/crash after offline video modules on 4 GB devices

**Reproduction and evidence**

1. Select a representative low-end device with 4 GB RAM and limited free storage; record model, OS build, RAM, storage, battery state, and app build. Repeat on an emulator configured to a low-memory device, but confirm on physical hardware.
2. Start from a clean install or controlled cache state. Download/play 3–4 training videos offline, navigate between modules, background/foreground the app, rotate if supported, and repeat with slow storage and low network during download. Keep exact steps, video sizes/codecs, and timestamps.
3. Capture:
   ```bash
   adb shell dumpsys meminfo com.example.survey
   adb shell dumpsys activity processes
   adb shell dumpsys activity oom
   adb shell df -h /data
   adb logcat -c
   adb logcat -v threadtime
   adb bugreport bugreport.zip
   ```
   Replace the package name. Search Logcat/bugreport for `ANR`, `am_anr`, `OutOfMemoryError`, `lowmemorykiller`, `SQLite`, `MediaCodec`, and `FATAL EXCEPTION`.
4. Use Android Studio Profiler for Java/Kotlin heap, native memory, CPU, and allocations; Perfetto/System Trace for main-thread stalls, I/O, binder waits, and scheduling; inspect tombstones/native crash output if relevant. Collect ANR traces where available.
5. Compare a failing and passing device with the same build/data. Correlate the exact ANR timestamp to main-thread stack, memory growth, video decoder resources, bitmap/video buffering, database work, and storage pressure. Prove root cause with a minimal reproduction and a before/after fix comparison.

**Controlled device manipulation**

- Low storage: use a dedicated emulator/device and fill test storage with disposable test files or large fixtures, monitoring `df -h /data`; do not fill a personal/production device or delete user data.
- Low memory: use Android Emulator device profiles and memory settings, close background apps, and repeat on physical 4 GB hardware. `adb shell am kill <package>` tests process recreation, but it does not itself simulate memory pressure while the process is running.
- CPU/network: Android Studio emulator controls, `tc` on a controlled lab gateway, or Charles/Proxyman throttling. Use consistent profiles and log them.
- Avoid relying on undocumented `setprop` tweaks as the sole evidence; behavior varies by Android version and often does not reproduce real low-RAM conditions.

Likely areas to investigate include unbounded video buffering, leaked player/decoder instances, large bitmaps, main-thread disk/database work, missing lifecycle cleanup, and repeated downloads/caches. These are hypotheses until the traces prove one.

### Scenario E — HFC (High-Frequency Checks) test plan

Assumption: `reask_and_flag` should show a clear explanation, allow correction, and avoid trapping the interviewer indefinitely. Exact threshold inclusivity and repeat limits must be agreed with product/domain owners.

| ID | Type | Inputs / action | Expected result |
|---|---|---|---|
| HFC-01 | Positive rule | Age=14, Education=College | Rule fires; question is re-asked and record flagged |
| HFC-02 | Negative rule | Age=15, Education=College | Does not fire if rule is strictly `Age < 15` |
| HFC-03 | Negative rule | Age=14, Education=Primary | Does not fire because education condition is false |
| HFC-04 | Positive rule | Sleep Well=Yes, Sleep hours=5 | Re-ask and flag |
| HFC-05 | Boundary | Sleep hours=6, Sleep Well=Yes | Does not fire if rule is strictly `< 6` |
| HFC-06 | Negative rule | Sleep Well=No, Sleep hours=4 | Does not fire because first condition is false |
| HFC-07 | Missing data | Age blank; Education=College | Do not coerce blank to zero; apply defined required/missing-data policy |
| HFC-08 | Type/boundary | Age `"14"` as string, decimal/negative/very large age | Validate/coerce per schema; no crash or silent incorrect comparison |
| HFC-09 | Edit recovery | Trigger rule, change Age to 16, re-evaluate | Clear active re-ask state/flag according to policy; preserve audit history if required |
| HFC-10 | Loop prevention | User repeatedly gives same invalid answer | Show explanation and apply a defined attempt limit/escalation; never infinite-loop |
| HFC-11 | Offline/restart | Trigger HFC offline, force-close, reopen draft | Rule state and answers are consistent after recovery |
| HFC-12 | Rule composition | Both rules true; multiple rules reference one question | Deterministic order; no duplicate dialog storm; all applicable flags recorded |
| HFC-13 | Performance | Large roster and many rules | Evaluation completes within agreed budget; no UI freeze |
| HFC-14 | Schema safety | Unknown operator or invalid question reference | Schema validation reports a clear error; no silent bypass or crash |

Also test AND/OR precedence, parentheses/nesting if supported, case sensitivity, nulls, locale-specific numeric input, rule version changes, and auditability of overrides. Log the rule ID, input snapshot, action, timestamp, and resolution without exposing unnecessary personal data.

## 3. Part 3 — QA process and AI integration

### First 30 days as the first dedicated QA Engineer

**Days 1–7: Understand and baseline**
- Meet engineering, product, design, and field operations; map the critical survey lifecycle from schema creation to offline capture and sync.
- Review architecture, release cadence, known incidents, analytics, and current test coverage.
- Create a risk register, device/network matrix, defect severity definitions, and a small smoke checklist.
- Establish a staging environment with test accounts, synthetic survey data, and safe reset/seed procedures.

**Days 8–14: Add lightweight release gates**
- Agree on entry criteria: story has acceptance criteria, schema/API changes are documented, test data is available, and feature flags/migration risks are identified.
- Agree on exit criteria: critical-path tests pass, no open release-blocking defects, offline data integrity is verified for impacted changes, and known risks are documented.
- Add PR checks for lint/build, unit tests, API contract tests, and a fast Playwright smoke suite. Keep checks parallel and short; run broader device suites nightly.
- Define severity/priority and a bug template with environment, steps, expected/actual result, logs, screenshot/video, and reproducibility.

**Days 15–21: Cover highest risks**
- Prioritize sync/idempotency, offline persistence, schema compatibility, skip logic, HFC rules, and app lifecycle.
- Build a small physical-device pool including low-RAM Android, different screen sizes, and supported OS versions.
- Add deterministic network-failure tests and database integrity checks to CI where practical.
- Start a weekly defect trend/risk review; pair with developers to keep failures actionable.

**Days 22–30: Make it sustainable**
- Add nightly regression and release-candidate runs, test reports, flaky-test tracking, and a documented triage owner.
- Establish a release checklist and rollback/kill-switch expectations for risky changes.
- Review escaped defects and cycle time; remove low-value brittle tests and improve high-value coverage.
- Publish a short quality dashboard: pass rate, escaped defects, flaky rate, critical-path coverage, sync failure rate, and crash/ANR trend.

This process should speed delivery by finding regressions early. Use risk-based gates and fast PR checks, not a slow full-device suite on every commit.

### AI in day-to-day QA

I would use an approved AI assistant to brainstorm boundary cases, draft test data, generate first-pass Playwright/API tests, explain stack traces, and summarize logs. I would not paste production personal data, credentials, tokens, or sensitive customer records. AI-generated tests are reviewed against requirements and run; they are not accepted as evidence merely because they compile.

**Specific Scenario B example:**
1. Provide a sanitized schema with question IDs/types, visibility expressions, validation rules, and roster definitions.
2. Ask the AI to enumerate a truth table for each skip rule, including true/false, null, boundary values, and changing a parent answer after a child answer is filled.
3. Generate parameterized test cases and Playwright assertions from the table.
4. Validate that the generated cases match the documented rule semantics, remove duplicates, and add them to version control.
5. Run the tests against the renderer and review failures manually. Use mutation tests or intentionally broken rules to check that the suite detects regressions.

### Automation tooling and prioritization

I chose **Playwright Test** because it supports browser automation, accessible-role locators, API requests, assertions, tracing, and CI integration in one TypeScript/JavaScript-friendly tool. A Node.js mock server gives a deterministic API contract example without requiring access to a real backend. It is deliberately a mock: real concurrency, persistence, authentication, and load characteristics must be validated in staging.

Automate when a check is repeatable, high-risk, deterministic, and expensive to repeat manually: sync idempotency, schema validation, skip logic, critical smoke paths, persistence, and regression. Keep manual/exploratory testing for comprehension, visual comfort outdoors, usability, ambiguous workflows, and novel interactions. Do not automate a low-value unstable test just to increase test count.

## 4. Part 4 — UI/UX and user-centric QA

For the Training Video Hub, I would test task completion in realistic field conditions, not only whether the video and Next button work.

### Risks to flag

1. **Dense text / low literacy:** three paragraphs may be difficult to understand. Replace with short plain-language instructions, clear headings, or optional audio/illustrations; validate wording with representative users.
2. **Next button below the fold:** users may not discover it after watching the video. Consider a sticky bottom action or a clear completion state; verify it does not cover content or system navigation.
3. **Small touch target:** ensure Next, play/pause, captions, and retry controls meet the team's accessibility target (commonly at least 48 dp for Android touch targets).
4. **Low contrast and sunlight glare:** measure text/control contrast and test outdoors or under a bright-light simulation; do not rely on color alone to convey state.
5. **Small screens and font scaling:** test 4 GB budget phones, narrow widths, large system fonts, display scaling, landscape/portrait if supported, and screen-reader focus order. Ensure no clipped or overlapping text.
6. **Video buffering on 2G:** verify loading, pause/resume, retry, offline playback, and clear progress/error messaging. Avoid infinite spinners.
7. **Storage pressure:** download multiple modules with low free space; communicate required storage and recover safely from partial downloads.
8. **Audio/caption accessibility:** confirm volume/mute behavior, captions or transcript availability, and understandable controls for noisy environments or users with hearing difficulties.
9. **Progress loss:** background/kill the app mid-video and verify playback/progress state is restored according to product requirements.
10. **Color-only feedback/icons:** labels and shapes should accompany colors; icons must be recognizable and consistently placed.

### Systematic evaluation

- Use a device matrix covering a physical low-end phone, small display, low memory, low storage, Android font scaling, screen reader, and both online/2G/offline conditions.
- Run Android Accessibility Scanner and automated accessibility checks, then manually inspect contrast, target size, focus order, text clipping, and talkback labels.
- Capture screenshots/video and device/build details for each defect. Repeat each issue to separate product defects from transient network/device problems.
- Conduct short moderated task tests with representative field workers where possible: “watch the lesson and continue,” without coaching. Record completion, errors, hesitation, and requests for help.
- File actionable bugs with impact and reproducible evidence, e.g. **“P1 usability blocker — Next action is below the fold on 320 dp screen at 1.3× font scale; 3/5 participants did not discover it within 30 seconds.”** Use measured evidence only—do not invent participant results.
- Offer a constructive recommendation and collaborate with design/dev on acceptance criteria. Prioritize issues that prevent safe data collection, block task completion, or cause data loss above cosmetic inconsistencies.

## Setup and execution

Requirements: Node.js 20+ and npm.

```bash
npm install
npx playwright install
npm test
```

The tests launch a local mock API and use a local HTML fixture. They do not need credentials or a live backend. To run only one test file:

```bash
npx playwright test tests/sync-api.spec.js
npx playwright test tests/skip-logic.spec.js
```

### Before submitting to the employer

- The fixture has been aligned to the supplied hobby skip rule. Expand it to cover all schema question types and operators before production use.
- The sync endpoint is still a mock contract and must be aligned to the backend team's actual API specification.
- Include employer reference files in the repository only if sharing them is permitted; do not commit confidential product data.
- Run the tests locally and commit any required lockfile (`package-lock.json`).
- Add a short note about Node/browser versions and actual test results after execution.
- Push this directory to a new GitHub repository and share that repository URL. This draft has not been pushed to GitHub.
