# Development guidelines

Update 2026-10-30 (Cloud preparation): [Cloud testing](CLOUD_TESTING.md) supersedes the earlier inline-key configuration described below. Runtime database/JWT credentials must now be external; existing values were preserved privately outside the source tree. Empty-database bootstrap requires an explicit password and never logs credentials. Use the ordinary H2 tests in Cloud and the mysql-it Failsafe profile on an owned MySQL runtime; GitHub Actions supplies the default Docker execution environment. Do not connect tests to a system datasource or assume the Flyway SQL chain bootstraps a database.

Security update 2026-10-30: at the user's explicit request, the signing key is stored directly in application.properties as app.jwt.secret; startup no longer requires JWT_SECRET. The temporary environment setup script was removed. Do not regenerate the configured key on builds or copy its value into documentation/logs. This reverts the external-key remediation of audit #2; the key is included in the JAR. Ordinary Maven tests retain their separate test-only key/H2/upload settings. User credential mutations must preserve row locking and DynamicUpdate; JWTs bind the immutable user ID and the provider's authenticated credential snapshot. Never reapply historical default permissions on existing operators. SecurityRegressionIntegrationTest, JwtServiceTest, SecuritySeedingAuditTest, AuthServiceCredentialSnapshotTest and UserCredentialConcurrencyIntegrationTest cover these changes; enable security.browser.enabled for the Chrome stored-XSS/session smoke. No schema migration was introduced. See [security launch notes](../audits/2026-10-30-security-fixes-fa.md).

## Melt assignment on an upgraded task schema — 2026-10-30

Do not assume recreating the latest task schema in H2 tests covers upgrades. A legacy MySQL task_type ENUM or enum CHECK can reject a new melt task with error 1265/3819; the common handler displays generic 409 conflict. TaskTypeSchemaRepair and V36 reconcile only these enum restrictions, preserving rows/current/mixed/business checks. The optional MeltTaskTypeMigrationMysqlTest reproduces both errors on a new loopback-only schema, tests both upgrade routes twice, and verifies legacy/null data and preserved constraints. The affected run passed 81 tests including that MySQL regression, melt workflow/passport/concurrency, task API and permission seeding. No default datasource was explored, and deployment remains unverified. See [melt passports](MELT_PASSPORTS.md).

## Text defect positions and reshoot/welding order — 2026-10-29

Positions now accept text endpoints while preserving the existing point/range structure and legacy API keys. Numeric JSON input is still accepted, and old numeric snapshots render correctly. With welded+reshootOk, reshoot appears before welding in server chain text, browser previews and print. V34 widens the five legacy position columns to VARCHAR(255); no records or signed snapshots are deleted/rebuilt.

Validation: **31 affected Maven tests passed** (RadiographyDefectIntegrationTest: 11, RadiographyWorkflowIntegrationTest: 11, DashboardWeldingReportsIntegrationTest: 8, RadiographyBrowserSmokeTest: 1), and **21 Node regressions passed**. Edited JS passed node --check. New cases cover text points/ranges, trimming, missing ends, duplicates, length limits, exact label identity/leading zeroes, numeric JSON compatibility, standalone text locations, and a signed final snapshot containing L4 and the revised welded/reshoot chain. Desktop/390px Chrome smoke passed add/edit/duplicate detection, save/reload, swapping review outcomes and print preparation; the print screenshot was visually inspected.

The opt-in RadiographyPositionMigrationMysqlTest also passed separately (`-Dradiography.positions.mysql.enabled=true -Dtest=RadiographyPositionMigrationMysqlTest test`) on a newly initialized loopback MySQL process at 127.0.0.1:13389 with only radiography_positions_synthetic fixtures. It does not load application datasource configuration. The test repeats V34 and LegacyDefectChainRepair, checks numeric/null/zero preservation, UTF-8 text and all five column lengths/nullability. This validates that standalone upgrade/repair on a minimal synthetic schema, not the full historical SQL chain or production deployment. The test instance is stopped after validation. No production database or uploaded files were used.

## Radiography catalog and reshoot result — 2026-10-29

The shared standard catalog now has 13 types plus OTHER/custom text across report chains, standalone defects, welding dispatches/maps and dashboard filters. Report-chain follow-up is exclusive: second defect, reshoot-OK, or neither. See BUSINESS_WORKFLOWS.md and DATABASE_STRUCTURE.md for DTO/storage semantics and the nullable V33 addition. Stored THICKNESS_MEASUREMENT is retained with display label «ضخامت مجاز».

Validation: **27 affected Maven tests passed** (RadiographyDefectIntegrationTest: 7, RadiographyWorkflowIntegrationTest: 11, DashboardWeldingReportsIntegrationTest: 8, opt-in RadiographyBrowserSmokeTest: 1), plus **18 existing Node regressions**. Edited application JS and the browser script passed node --check. Coverage includes all standard types, required OTHER text in either position, exclusive review outcomes and switching/removal, null legacy flags, report-only outcomes and permissions, unchanged course/piece state, new catalog values in welding/map/dashboard paths, and reshoot data in the signed final report snapshot. Chrome desktop/390px mobile exercised save/reopen/reload, escaped custom text, switching and removal; the actual print-root preparation was used for a generated PDF and visually inspected screenshot under target/radiography-browser/.

Reproduce with Maven `-Dtest=RadiographyDefectIntegrationTest,RadiographyWorkflowIntegrationTest,DashboardWeldingReportsIntegrationTest,RadiographyBrowserSmokeTest -Dradiography.browser.enabled=true test` and `node --test src/test/js/*.test.cjs`. The opt-in browser test uses a random loopback port, its own synthetic H2 database and target/test-uploads/radiography-browser. Windows sandbox javac JAR path resolution failed; the successful Maven runs used the normal local dependency cache outside that sandbox. No default/production datasource or production uploads were used. V33 MySQL execution, deployment and real concurrent production requests remain unverified; H2 does not validate MySQL migration SQL.

## Employee performance — 2026-10-24

See [employee performance](EMPLOYEE_PERFORMANCE.md) for behavior, contracts, V31 and concurrency decisions. This checkout still has no Git metadata; the existing files edited by this work were preserved locally in target/performance-baseline/before.zip before modification.

Validation: full Maven suite with `-Dperformance.browser.enabled=true -Dperformance.mysql.enabled=true`: 199 tests, 196 passed, 3 existing melt MySQL opt-in cases skipped, zero failures/errors. The performance MySQL cases and additive migration/JPA validation ran on a dedicated synthetic MySQL 8.0.31 at 127.0.0.1:13387, with separate performance_synthetic/performance_ddl_synthetic databases. The browser smoke uses synthetic users, a random HTTP port, an isolated H2 database and target/test-uploads/performance-browser. All 34 application JS files and the new smoke script passed node --check; 15 existing Node regressions passed. Chrome desktop/mobile confirmed grading, rollover, closed correction, escaped snapshots, access boundaries, stale navigation, 409 refresh and the standalone-confirmation/main-dialog lifecycle fix. Default datasource, production uploads, production migrations and deployment were not exercised.

## Dashboard and welding changes — 2026-10-24

Implemented: tensile quality/trend CSV exports, substage rows and CSV fields in the stage report, open welding dispatch editing, and READY exclusion from stuck alerts/totals. Regression coverage is in `DashboardWeldingReportsIntegrationTest` (8 cases) and `src/test/js/dashboard-welding-reports.test.cjs` (4 cases). The opt-in `DashboardReportsBrowserFixture` binds only to 127.0.0.1:18085, explicitly uses an isolated synthetic in-memory H2 database, and writes uploads under `target/test-uploads/dashboard-reports-browser`; run `src/test/js/dashboard-welding-reports.browser.cjs` against that fixture.

Validation: **49 affected Maven tests passed** (`DashboardWeldingReportsIntegrationTest`, `ProductionTrackingIntegrationTest`, `RadiographyWorkflowIntegrationTest`, `TensileTrendReportIntegrationTest`); all **15 Node regressions** and application JavaScript syntax checks passed. Headless Chrome verified UTF-8 BOM CSV downloads, quality range filtering, trend values, substage labels/CSV, saving and reopening the welding form, escaped text, and 390px mobile layout with no page overflow or JavaScript errors. Final screenshots and synthetic CSVs are generated under `target/dashboard-reports-browser/`. The isolated fixture was stopped after validation. No default datasource or production uploads were used. No schema changes were made; MySQL behavior and concurrent production requests were not separately tested.

Reviewed: 2026-10-16. These rules are derived from the current source. Recommendations are future work, not changes implemented by this review. Read [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) and the relevant workflow before editing.

## Establish the change boundary

1. Confirm the requested behavior and identify every entry path: controller, service, repository, DTO, browser wrapper, UI caller, and affected reports/jobs.
2. Check for working-tree changes before editing. This checkout has no Git metadata; establish version control or an approved baseline before future implementation work.
3. Read local `AGENTS.md`. Do not change generated `target/` or runtime `uploads/` as source fixes. Preserve Persian UTF-8 text, API field names, existing enums and compatibility aliases.
4. Keep business rules in services/policies and authorization on the server. Use DTOs for HTTP boundaries and `@Valid`/Jakarta constraints for request shape; domain consistency still needs service checks.
5. Avoid introducing a frontend framework, build chain, new role system or module restructuring as an incidental change. Existing tasks demonstrate how to split cohesive services incrementally.

## Safe environment and commands

The application defaults can connect to a real MySQL database and automatically mutate schema, data and permissions on startup. **Do not run the normal application or migrations against those defaults as an exploratory check.** For local execution, deliberately supply an isolated database, synthetic records, separate upload directory and secret settings outside source control. Verify schema management and startup runners before connecting even a new MySQL instance.

Standard build/test commands from the repository root:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dtest=TrendAnalyzerTest test
.\mvnw.cmd -Dtest=RadiographyWorkflowIntegrationTest test
.\mvnw.cmd -Dtest=TaskAccessPolicyTest,TaskApiIntegrationTest,TaskCollaborationIntegrationTest test
```

Use an installed/cached Maven executable if the wrapper cannot initialize; do not patch application code to solve a machine-specific wrapper problem. `-o` is appropriate only when required Maven artifacts are cached. Avoid `spring-boot:run` without explicit isolation. Building with tests skipped does not establish behavioral correctness.

Test resources configure H2 in-memory MySQL mode, create/drop schema, Flyway disabled, a test upload directory under `target/`, and reminders disabled. `TaskTestBase` uses MockMvc, real login/JWT and per-test rollback. Application startup runners still execute during context creation; tests passing does not prove they are harmless or inactive.

Frontend syntax checks require no package installation:

```powershell
Get-ChildItem src/main/resources/static/js -Filter *.js | ForEach-Object {
    node --check $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax check failed" }
}
```

Node is only a validation tool here; the frontend runs in the browser and has no Node build requirement.

## Validation baseline from this review

On 2026-10-16, Maven 3.9.16 with JDK 25.0.3 ran the existing suite offline: **115 tests passed, 0 failures/errors/skips**. POM compilation target is 17; this is not a separate JDK 17 validation.

| Area | Existing tests / executed count |
|---|---|
| Application context | `ProductManagerApplicationTests`: 1 |
| Task policy | `TaskAccessPolicyTest`: 7 |
| Task HTTP workflows | `TaskApiIntegrationTest` nested groups: 46 |
| Task collaboration/notifications/reminders | `TaskCollaborationIntegrationTest` nested groups: 23 |
| Radiography approval | `RadiographyWorkflowIntegrationTest`: 11 |
| Tensile trend API/service | `TensileTrendReportIntegrationTest`: 11 |
| Trend mathematics | `TrendAnalyzerTest`: 16 |

Nested parent classes may report zero while their child suites run tests; use the Maven aggregate rather than counting only top-level report files. Coverage includes permission/resource isolation, task status/fields/audit/relations, comments/checklists/notifications, reminder deduplication, radiography signature/approval/rejection/locking behavior, and trend filtering/math.

At the initial discovery review, no dedicated automated browser suite, MySQL migration suite, comprehensive normal production-transition suite, tensile approval/correction suite, or heat-batch/storage rollback suite was found. The follow-up tracking tests below add focused search, display and approval regressions; remaining gaps still apply. Sequential double-approval tests do not prove concurrent report-edit safety.

Additional review checks passed: all **32 JavaScript files** passed `node --check`; all local links in the six context documents resolved; SHA-256 comparisons found **0 changed or missing files among 394 existing source/build inputs** (`src/`, `.mvn/`, `deploy/`, POM and Maven scripts). The only authored changes are these six Markdown documents and root `AGENTS.md`; tests generated outputs under `target/`. No production database was accessed and no sensitive configuration/credential values were copied into the documents. Test/generated logs may include bootstrap or fixture credentials: do not copy raw logs into documentation, tickets, or external messages.

## Domain invariants and high-risk edits

- **Movement:** use `PieceService` and `StageTransitionPolicy`; do not set stages ad hoc in controllers/UI. Test ordinary operator, admin correction, trusted disposition, dated advance, generic/bulk paths, archive state, history and date side effects. Welding dispatch/return is a separate route.
- **Radiography:** use workflow operations for approval-task transitions. Preserve unique open workflow, assigned interpreter checks, explicit confirmation, complete report lines, signatures, locked manager review and stored snapshots. Test legacy defect APIs and template/read synchronization too; they currently have different guards.
- **Tensile:** preserve BigDecimal comparisons and the distinction between incomplete and failing data. Test equality/null minima/missing values, auto approval, manual disposition, aging/re-operation, downstream correction and deletion behavior. Inspect `inspectionResult` and `nextStage` independently.
- **Heat:** distinguish logged operation stage strings, enum production substages and tensile aging routes. Test existing-batch reuse, duplicate membership, optional same-piece disposition, explicit batch edits and last-member deletion.
- **Dates/reports:** keep actual process dates separate from event timestamps/test creation times. Test repeat-stage visits and date boundaries before changing metrics. Do not make all dashboard filters uniform without an explicit product decision.
- **Permissions:** keep `ADMIN`/`OPERATOR` plus existing permission codes. `MANAGE_PIECE_ASSIGNMENT` means tensile entry; do not rename persisted codes casually. Test restart seeding as well as live permission revocation.
- **Task resources:** apply `TaskLookup`/`TaskAccessPolicy` to both parent task and nested resource ownership. Preserve 404 for invisible resources and 403 for forbidden actions on visible resources. Do not allow generic task endpoints to bypass radiography decisions.
- **Retention:** pieces archive, tasks cancel, projects/definitions hard-delete. Test dependencies before any deletion change. Historical names/snapshots/files have different retention semantics.

## Persistence, transactions and files

Choose a single transactional service boundary for a business mutation and include related history/notifications in it. Annotating a method does not create a transaction for same-instance calls. Use an externally proxied service boundary or explicit transaction handling where needed; preserve existing semantics until the change is authorized.

Do not assume `@Transactional` serializes users. For competing edits, plan explicit optimistic/pessimistic locking or uniqueness/retry behavior and meaningful conflict responses. Current entities lack `@Version`, and several operations use check-then-write or max+1 allocation.

Schema work must first resolve the incomplete migration story. Never rewrite historical SQL checksums or add a new enum and assume Hibernate will repair every existing MySQL database. Use a disposable MySQL clone to establish required baseline, migrations/backfills, schema diffs and rollback/restore procedure. Review startup repairs and permission seeding alongside the new migration. H2 green tests cannot validate MySQL `information_schema`, prepared DDL, ENUM, collation, indexes or delete actions.

Database rollback does not undo file replacement/deletion. Model storage changes around commit/rollback; preserve independently copied approval signatures. Use server-controlled relative paths and validate upload type/size. Test invalid replacement uploads and simulated persistence failure. Keep database and file backups coordinated.

Read paths should avoid unintended writes. Existing radiography report retrieval synchronizes lines; do not annotate it/read callers `readOnly` or add caching without accounting for that behavior. Prefer page/projection/batch queries and existing entity graphs over fetching large collections; measure before optimizing.

## Frontend changes

Keep endpoint wrappers in `api.js`; preserve JSON/multipart/blob behavior and 401 handling. Use server-provided movement metadata. Escape user content for its rendering context; never interpolate untrusted text directly into HTML or event attributes. Keep localStorage session handling compatible.

Preserve classic-script order/global names and DOM IDs. Update the matching `?v=` in `index.html` when JS/CSS changes because assets are cached for seven days. Exercise stale-request handling, selection/reset state, nested dialogs, RTL, Jalali conversion, keyboard focus, theme changes, Canvas hover/redraw, printing and CSV output on affected screens.

Manual regression choices for a future affected change:

| Change | Scenarios |
|---|---|
| Auth/permissions | Admin and limited operator login, direct denied API calls, permission revoke, disable account, expired token, logout, restart grants |
| Production | Piece creation/bulk conflict, every normal substage exit, dated advance, generic/bulk route differences, scrap/admin correction, archive/restore |
| Radiography | Interpreter-only versus editor permissions, assign/reassign, incomplete issue, signature replacement, issue/reject/reissue/approve, direct defect mutation during review, repeated/concurrent decision, post-closure live report |
| Tensile/heat | Missing/equal/below thresholds, automatic/manual decisions, correction after movement, aging rounds, batch reuse/edit/delete |
| Tasks | Creator manager/assignee/outsider, nested ID mismatch, revoked manager permission, terminal task, workflow task, deadline timezone/duplicate scheduler |
| Reports/UI | Present versus period metrics, repeated stage visit, archived pieces, date boundaries, RTL/date input, filter pagination, print/CSV/theme behavior |

## Recommended implementation sequence

1. Establish version control/baseline and isolated reproducible MySQL bootstrap/upgrade checks; verify whether Flyway executes before changing schema management.
2. Add targeted characterization tests around radiography defect/report locking and permission provisioning, startup permission grants, tensile corrections and storage failures.
3. Agree on product decisions: mandatory radiography approval, final report editability, distinct approvers, post-review tensile correction, repeated-visit process dates and deletion retention.
4. Implement the agreed integrity/access fixes with narrow tests and explicit database/file handling.
5. Gradually extract cohesive pieces of `PieceService`/`DashboardService`, reduce duplicated transition logic, and measure large-report/query costs. Avoid a broad rewrite before invariants are covered.

## Documentation maintenance

When behavior changes, update the relevant context document and source references in the same change. Label observations, risks and unresolved deployment assumptions separately. Never include connection strings, database names/hosts copied from configuration, credential/token values, user signatures, uploaded drawings, or production rows. Keep `AGENTS.md` concise and actionable rather than copying this entire review into it.

## Production tracking improvements — 2026-10-16

Implemented: dashboard current scrap count; exact numeric piece search without resetting selected tracking stage; display-only latest relevant tensile repeat/re-operation/aging context and separate recorded aging-duration boxes; existing history order and fixed production route retained while showing the existing execution dates. No schema migration, new stage/substage, approval rule or production transition was introduced.

Validation executed: **132 Maven tests passed** (115 existing + 17 `ProductionTrackingIntegrationTest` cases), with zero failures/errors/skips, on isolated H2; **6 Node frontend regression tests passed**; all 32 application JavaScript files passed syntax validation. Commands: cached Maven `-o test`, `node --test src/test/js/production-tracking.test.cjs`, and `node --check` for each application script. The initial dashboard test used an incorrect property name and was corrected to the actual API field before the successful final runs.

Browser checks used an explicitly isolated, loopback-only H2 application and synthetic API-created fixtures. Verified exact 9 versus 19/90/109/219, 90-minute aging label, existing radiography/heat/rough substages, stage-preserving search intersection, light/dark rendering, and 390px mobile dashboard/table containment. The application retains horizontal scrolling for the wide mobile tracking table. Screenshots are local generated artifacts under `target/ui-verification/`. No production database or upload files were used. The original date-sorted rail check was superseded by the follow-up correction below.

Remaining limits: H2 does not establish MySQL execution-plan compatibility. There is still no per-visit process-date/cycle model; aging durations use test-index order and the latest approved test as a display boundary. Scrap card wording deliberately follows SCRAPPED/«اسقاط», rather than the radiography SHOT/«شوت» label. No further business clarification is pending.

Modified source files (paths relative to the repository root):

| Area | Files |
|---|---|
| Search | `src/main/java/com/factory/productmanager/repository/PieceSearchTerm.java` (new), `repository/PieceRepository.java`, `repository/PieceRepositoryImpl.java` under the same Java package |
| Tensile display | `repository/TensileTestRepository.java`, `repository/TensileTrackingRow.java` (new), `service/TensileTrackingService.java` (new) under that package |
| Piece responses/history | `service/PieceService.java`, `model/dto/PieceResponse.java`, `model/dto/PieceStageHistoryResponse.java`, `model/dto/PieceSubStageHistoryResponse.java`, `model/dto/TensileTrackingInfoResponse.java` (new) under that package |
| Browser UI | `src/main/resources/static/index.html`, `js/dashboard.js`, `js/tracking.js`, `js/production-flow.js`, `css/style.css` under the static directory; matching asset cache versions updated |
| Regression tests | `src/test/java/com/factory/productmanager/task/ProductionTrackingIntegrationTest.java` (new), `src/test/js/production-tracking.test.cjs` (new) |
| Context documentation | `docs/ai-context/BACKEND_ARCHITECTURE.md`, `FRONTEND_ARCHITECTURE.md`, `BUSINESS_WORKFLOWS.md`, this document |

## Tracking display correction — 2026-10-16

The profile rail and stage/substage history list now keep their original production-route and recorded-time orders. Existing process dates remain visible beside each stage; they no longer reorder the route or history rows. Tensile tracking shows the latest decision without the «تعیین‌تکلیف» prefix. Each aging disposition after the latest approved test with a stored duration receives its own tensile-colored minute box. Missing durations produce no box. The latest decision remains visible beside those boxes, including NEXT_TENSILE after multiple aging rounds. A current visible decision is not suppressed just because legacy stage history cannot prove the return path.

Validation at the time: all **133 Maven tests passed** on isolated H2, including **18** production tracking integration tests; all **6** Node UI regression tests and all **32** application JavaScript syntax checks passed. The subsequent reproduction/fix is being validated in the current turn. No production database was accessed.
