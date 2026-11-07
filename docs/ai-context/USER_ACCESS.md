# User access — category 1

Implemented scope: MANAGER / ADMIN / OPERATOR; explicit ADMIN grants; manager-only account/role/permission management; atomic name/role/grant edits; last-active-manager protection; append-only account-change history. The previously proposed categories 2–7 are not part of category 1. A later change implements only selected items 2–1/2–2; see [Stage movement access](STAGE_MOVEMENT_ACCESS.md).

## Delegated access — 2026-11-07

- `VIEW_USERS` exposes a read-only directory projection through `GET /api/users/directory`. The account-management API and access history are manager-only.
- `EDIT_PERFORMANCE_SCORES` authorizes score edits and evaluation visibility independently of task and directory access. Periods and criterion structure are controlled by MANAGER.
- `ASSIGN_MELT_OPERATOR` authorizes melt-specific assignee lookup and assignment independently of task management.
- MANAGER has implicit access. Assign these grants explicitly to eligible ADMIN or OPERATOR users.

## Role semantics

- MANAGER (مدیر) has all existing permissions implicitly and inherits former full-access ADMIN exceptions, including task administration, profile/status administration, performance, production corrections and melt administrative operations.
- ADMIN (ادمین) and OPERATOR (اپراتور) use their explicit existing Permission enum values. ADMIN has no role-wide bypass, even with all grants; account administration remains MANAGER-only.
- Existing ADMIN/OPERATOR codes and all permission identifiers remain valid. Fine-grained skip/rollback/scrap permissions are deferred to category 2. Existing manager-only special actions cannot yet be delegated separately.
- Client `isManager()` and backend permission/policy checks agree. Role-only guards are MANAGER guards. No role hierarchy grants ADMIN manager authority.
- Changing a role or explicit grant changes the JWT credential binding; old sessions must sign in again. The server still loads current user authorities per request. Full-name edits preserve authentication. Updating an existing MANAGER's name preserves its stored grant set.

## API and UI

- Existing create/list/delete/permissions/password/profile mutation routes now require MANAGER.
- `PATCH /api/users/{id}` accepts `{fullName, role, permissions}` atomically. Username and identity stay unchanged; `permissions` must be an explicit array, including empty. A MANAGER has all effective grants and needs no grant checkboxes.
- `GET /api/users/{id}/access-history?page=0&size=25` returns PageResponse, bounded to 100 rows, newest-first with ID tie-breaker. History survives deleted subjects and contains actor/subject IDs, action, timestamp and before/after JSON. No credentials, password hashes, signatures or tokens are captured. No mutation/delete API exists for audit events.
- The users table exposes separate name/role editing, permissions for ADMIN and OPERATOR, and paginated history. Untrusted names/snapshots are escaped. The new role controls use the existing UI.
- Profile department/active edits are audited in the same transaction as the change. Self-delete/self-disable restrictions remain. The last enabled MANAGER cannot be demoted, disabled or deleted.

## Transaction and concurrency design

All account create/delete, role/grant/profile writes and administrative password resets acquire ordered pessimistic locks on the current user rows before checking the live actor. Last-manager decisions use those locked entities, not a repeatable-read COUNT snapshot. This deliberately serializes the low-volume administration surface. Workflow, password and profile row locks remain relevant; do not add administration callers that lock individual users first and then request the full lock set.

The audit insert participates in the same transaction. Failed validation/authorization, last-manager rejection, failed deletion and no-op snapshots do not add successful-change events. Stored snapshots are an application audit trail, not protection against direct database-administrator modifications.

## Upgrade — explicit manager selection required

No production/default datasource was started or modified during implementation. Do not point CI at runtime credentials.

1. Review and back up the runtime database and uploads using the existing operational procedure.
2. V38 preserves role strings and widens `users.role` to VARCHAR(32); it creates `user_access_events` without user foreign keys so history outlives deletion. Current inspected users.role is already VARCHAR(32), with no role CHECK. LegacyMysqlEnumRepair runs before manager selection and already converts legacy role ENUM. Unknown custom role CHECKs require inspection before upgrade.
3. Existing installations with no enabled MANAGER must set `APP_INITIAL_MANAGER_USERNAME` to the exact username of the enabled existing ADMIN explicitly selected to be manager. No username is guessed; no bulk promotion occurs. A missing/disabled/non-ADMIN selection fails startup without promoting anyone.
4. InitialManagerSetup performs that one promotion and adds `INITIAL_MANAGER_SELECTED` with a system actor. Once an enabled MANAGER exists, the old setting never promotes anyone again. Remove the setting after successful upgrade.
5. Other ADMINs retain stored explicit grants but lose implicit full access; review them with the selected manager. Accounts, IDs, passwords, signatures, tasks and production records are not recreated. Empty-database bootstrap retains the legacy environment variable `APP_BOOTSTRAP_ADMIN_PASSWORD` and username `admin`, creating a MANAGER directly.
6. Reconcile V38 with the installation's schema procedure. Existing Hibernate update/startup repairs are unchanged; Flyway is not enabled by this change and its incomplete chain must not be enabled blindly. The sanitized MySQL fixture now has 49 tables, including the audit table, and retains 65 foreign keys.
7. Old JWTs require sign-in after the upgraded credential binding. Test role changes and password login on an isolated copy before production deployment.

## Validation and limits

- 25 Node regressions passed (22 existing plus 3 role/UI permission regressions); all 35 application JavaScript files passed `node --check`.
- Added H2/API regressions: manager vs restricted admin, all-grants admin unable to manage accounts, atomic edits and audit privacy, name-only login preservation, old JWT rejection, last-manager rejection, explicit startup selection and concurrent self-demotion.
- Added isolated MySQL V38 preservation/repeatability test and inherited API suite; updated the fixture table-count assertion and legacy full-access test accounts to MANAGER. The common test login helper renews ordinary request tokens after state changes; explicit stale-token regressions retain their original tokens.
- JDK 17 parsed all 422 main/test Java source files with zero syntax errors; parsing does not check types, compile, or execute tests. Chromium is not installed, so no real-browser smoke run was possible.
- Local Maven online/offline attempts stop before compilation: Spring Boot 4.1.0 parent is unavailable in cache and Maven Central DNS cannot resolve. No Java, H2, MySQL or live-application browser result is claimed locally. Docker is absent. These checks must pass in CI before merge/deployment.

### CI follow-up: scrap test isolation

The first PR run successfully compiled Java and passed the isolated MySQL job. The fast job found one existing test-order dependency: `ScrapIntegrationTest.bulkFailureRollsBackTheFirstPiecesReasonStageAndHistory` runs without a surrounding test transaction to exercise actual rollback, then commits two successful scraps. Those fixtures survived in the shared H2 `testdb`, so the later global tracking counter expected 2 but found 4.

ScrapIntegrationTest now uses its own synthetic H2 database/upload directory. The MySQL subclass similarly selects a dedicated `scrap-mysql` profile and the owned `scrap_workflow_synthetic` schema through IsolatedMysql. All existing assertions (including exact global scrap counts) and runtime behavior remain unchanged. Full CI must be rerun on this fix; prior green MySQL results are not results for the new commit.
