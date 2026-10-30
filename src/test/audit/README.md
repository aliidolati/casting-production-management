# Local audit tools — 2026-10-30

Historical audit snapshot: findings #1, #2, #3, #5 and #6 were subsequently fixed. These real-database scripts document the original investigation; they are not the current regression suite and some deliberately rely on the old signing-key/token design. Use SecurityRegressionIntegrationTest, JwtServiceTest, AuthServiceCredentialSnapshotTest, UserCredentialConcurrencyIntegrationTest, SecuritySeedingAuditTest and the opt-in SecurityBrowserSmokeTest for the fixed implementation. SecuritySeedingAuditTest now asserts preservation of permission decisions. The default application was not restarted by the implementation task. See docs/audits/2026-10-30-security-fixes-fa.md for launch requirements.

These tools were used for an explicitly authorized audit of the already-running local test application on loopback port 8080 and the project's configured local MySQL test database. They are outside Maven's automatic test discovery. Do not run them against a production database. They create privileged, temporary audit accounts, synthetic projects and workflow data, and exercise real mutation endpoints.

The ordinary Maven tests still use their existing isolated H2/test-upload configuration. No test properties were redirected to the configured MySQL database.

## Files

- `LocalAuditDatabase.java`: reads configured loopback MySQL credentials in memory; exports schema metadata/counts with credentials redacted; seeds a fresh audit administrator; removes only explicitly namespaced audit roots and their foreign-key descendants. Cleanup deletes referenced synthetic upload files after committing the database deletion. It does not disable FK checks, reset schemas, run migrations or start Spring.
- `local-api-audit.cjs`: real API, workflow, negative authorization, integrity and optional headless Chrome checks. Passwords and tokens remain in memory; responses containing records/files/signatures are not exported. `FAIL` means the current implementation violated an audit criterion, not that the test driver necessarily crashed. Inspect any `ERROR` separately.
- `token-lifecycle-audit.cjs`: isolated account lifecycle probes, including the currently configured signing key and username reuse. It cleans its own namespace in `finally`.
- `route-closure-audit.cjs`: covers remaining controller routes against the main run's still-existing synthetic fixtures. It deliberately uses the current signing-key weakness with the audit account only. After fixing signing-key deployment, replace this authentication setup with a legitimate audit session before reusing it.
- `build-audit-summary.cjs`: combines final recorded checks and builds a controller-route coverage table. A 2xx response establishes route exercise, not complete correctness.
- `../java/com/factory/productmanager/service/SecuritySeedingAuditTest.java`: now three Mockito-only regression tests requiring empty, revoked and explicitly granted permissions to remain unchanged on startup. They do not connect to any database.

## Preparation

Run Maven tests normally with the existing test configuration first. Use the installed/cached Maven and its actual local repository; on the reviewed machine Java's default user home did not locate the correct repository, so an explicit `-Dmaven.repo.local=...` was required. Recompilation also required access to the cached dependency JARs outside the workspace; the eventual two seeding tests passed.

The standalone Java helper needs the same dependency classpath as a successful Maven test run. It can be exported without downloading a plugin:

```powershell
New-Item -ItemType Directory -Force target/audit | Out-Null
[xml]$auditTestReport = Get-Content target/surefire-reports/TEST-com.factory.productmanager.service.SecuritySeedingAuditTest.xml -Raw
$auditClasspath = ($auditTestReport.testsuite.properties.property |
    Where-Object { $_.name -eq 'java.class.path' } | Select-Object -First 1).value
Set-Content target/audit/classpath.txt -Value $auditClasspath -Encoding utf8 -NoNewline
```

The reviewed environment had an earlier equivalent classpath at `target/melt-workflow-classpath.txt`; the Node tools accept it as a fallback. Neither generated file is source configuration. The optional Chrome check uses the Playwright runtime path recorded in the existing browser tests and needs adjustment on another machine.

## Ordered execution, only after local-test-database authorization

```powershell
$auditCp = Get-Content target/audit/classpath.txt -Raw
java --class-path $auditCp src/test/audit/LocalAuditDatabase.java --authorized-local-test-db inspect
Copy-Item -LiteralPath target/audit/database-metadata.json -Destination target/audit/before-final-database-metadata.json

node src/test/audit/local-api-audit.cjs --authorized-local-test-db --browser
node src/test/audit/token-lifecycle-audit.cjs --authorized-local-test-db
node src/test/audit/route-closure-audit.cjs --authorized-local-test-db
node src/test/audit/build-audit-summary.cjs
```

Keep the main fixtures until route closure and browser checks finish. Failed security criteria are expected in the audited snapshot; they must be reassessed after fixes. Do not convert their expected secure outcomes into insecure expectations merely to make the audit green. Earlier pilot runs and contract corrections are not included in the final totals.

## Cleanup

The main driver intentionally leaves its fixtures available for inspection and supplemental tests. Remove them using the exact namespace in its manifest:

```powershell
$auditRoots = Get-Content target/audit/fixture-roots.json -Raw | ConvertFrom-Json
@{run=$auditRoots.run} | ConvertTo-Json -Compress |
    java --class-path $auditCp src/test/audit/LocalAuditDatabase.java --authorized-local-test-db cleanup
java --class-path $auditCp src/test/audit/LocalAuditDatabase.java --authorized-local-test-db inspect
```

Each namespace starts with `AUDIT_20261030_` and an eight-character random identifier. If interrupted, recover the namespace from `fixture-roots.json` or `results-AUDIT_*.json` and clean that namespace explicitly. Never broaden cleanup to non-audit rows. For this audit all namespaces were cleaned, all four audit-root counts were zero, and before/after row counts matched for all 46 tables. `target/audit/cleanup-verification.json` records the final check.

Detailed logs under `target` may contain ordinary test fixture credentials produced by existing frameworks; do not publish raw logs. The delivered report and coverage table contain no live token/password/signature/drawing contents.
