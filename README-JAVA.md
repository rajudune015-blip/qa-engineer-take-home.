# QA Engineer Take-Home Assessment — Java version

This is a Java/Maven conversion of the JavaScript Playwright assessment project. It contains:

- Java mock sync API using the JDK `HttpServer` and Jackson.
- JUnit 5 API tests for idempotency, conflicting submissions, required fields, and device/local-ID collisions.
- Playwright for Java UI tests for the actual `q_3` → `q_4` hobby skip-logic example.
- The assessment README, test matrix, schema review, and fixture HTML.

## Requirements

- JDK 17 or newer
- Maven 3.8+

## Run

From this directory:

```bash
mvn test
```

Playwright Java needs its browser binaries installed once. If Chromium is missing, run:

```bash
mvn -q -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium" org.codehaus.mojo:exec-maven-plugin:3.5.0:java
```

Alternatively, install Playwright's Chromium using the CLI for your environment. The tests launch Chromium headlessly.

## Notes

- The API server is a local mock on `127.0.0.1:4173`; it is not the employer's live backend.
- The server's in-memory data is reset before each API test. Do not use this mock as a production implementation.
- `README.md` contains the original assessment response and test strategy. `SCHEMA_REVIEW.md` records defects found in the supplied sample schema.
