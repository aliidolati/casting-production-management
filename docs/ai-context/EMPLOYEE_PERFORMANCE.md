# Employee performance evaluations

Implemented 2026-10-24. This module is independent of tasks, task statistics, production movement and workflow approvals. Deployment and production schema/data are not inspected.

## User behavior

The profile owner can read their own periods and history. MANAGER controls period and criterion structure. `EDIT_PERFORMANCE_SCORES` grants evaluation visibility and score-only edits for other users, including disabled accounts, with audited reasons. The evaluation-only profile path shows identity and evaluation data. `VIEW_USERS` independently provides a read-only directory. `MANAGE_TASKS` grants no evaluation access.

Each employee has independent, manually named periods (for example آبان ۱۴۰۵), at most one open period, and per-period criteria. A criterion contains title, description, maximumScore, weight and score. New criteria default to maximumScore and can have a different initial score with a reason. Changing the score requires a reason separate from the criterion description. Inputs accept up to two decimal places, at most ten integer digits, positive maximum/weight and a non-negative score. A bonus may raise the score above the base maximum.

The server computes `sum(score * weight) / sum(maximumScore * weight)` with BigDecimal and rounds only the final division to four decimal places, HALF_UP. Maximum score as well as weight affects a criterion's contribution. A single 40/50 criterion with weight 3 produces 0.8000; adding a full 50/50 criterion with weight 1 produces 0.8500. No active criteria means null efficiency (JSON may omit null fields), not zero or one. The UI displays the weighted totals and ratio, including values above one for bonus scores. For a single 110/100 criterion the ratio is 1.1000.

Starting the next period atomically closes the old period and copies only its active criteria into independent new rows at full score. It is manual per employee; there is no calendar rollover, bulk reset, common template, task-derived grade or ranking. Period labels do not impose calendar boundaries. Recorded timestamps use the application's injected Clock, API timestamps are ISO and the existing UI helpers display Jalali dates.

Deleting a criterion soft-deletes it: it leaves the calculation and future copies, while its snapshot remains in history. Any criterion add/edit/delete in a closed period requires a reason, keeps that period closed, and does not modify its successor. Events retain copied actor ID/username/name, before/after criterion values and before/after efficiency. The original closing result remains in the closure event even after later corrections. Events have no write/delete API. Accounts with evaluation periods cannot be hard-deleted; administrators can disable them.

## HTTP contracts

Base: `/api/users/{userId}/performance/periods`. All endpoints require authentication. Resource ownership is checked across user, period and criterion. Invisible evaluations return 404; a visible owner's attempted write returns 403. Evaluation data is absent from UserProfileResponse. The separate `GET /api/users/{userId}/performance` endpoint provides only employee identity under evaluation visibility rules.

| Method / suffix | Behavior / request |
|---|---|
| GET base | PageResponse of period metadata; page=0, size=12 by default, size clamped to 1–100; newest created first |
| POST base | First period only: `{title}`; subsequent creation must use next |
| GET `/{periodId}` | Consistent period detail, criteria, weighted totals, efficiency, version and canManage/canEditScores/canStartNext |
| POST `/{periodId}/next` | `{version,title}`; closes and copies atomically; returns the new open period |
| POST `/{periodId}/criteria` | `{version,title,description,maximumScore,weight,reason}`; score may be omitted to default to the base maximum. An explicit different score requires a reason |
| PATCH `/{periodId}/criteria/{criterionId}` | Same fields plus mandatory score; full criterion replacement; reason required for score changes or closed-period edits |
| PATCH `/{periodId}/criteria/{criterionId}/score` | `{version,score,reason}`; score-only update with an audited reason for changes |
| DELETE `/{periodId}/criteria/{criterionId}` | JSON body `{version,reason}`; reason required for closed periods; returns updated detail |
| GET `/{periodId}/events` | PageResponse of audit events; page=0, size=20 by default, size capped at 100; newest first |

Period status is OPEN or CLOSED. Criterion IDs change on copy. Invalid requests return 400; stale versions, duplicate starts and conflicting persistence operations return 409. Old/new event values are JSON snapshot strings; neither these nor client-provided totals are used to mutate live criteria.

## Persistence and concurrency

V31 adds performance_periods, performance_criteria and performance_events. The period has @Version and an explicit revision counter so every criterion mutation dirties/increments the aggregate even if the timestamp is unchanged. A unique nullable open_user_key is the user's UUID string while open and null after closure. The key is flushed to null before successor insertion. User references restrict deletion; event actors are copied scalar fields, preserving identity independently of actor deletion. Criterion deletion is logical; no API deletes periods or events.

Writes lock the target user then the owned period, check the client's version, then access criteria. Initial-period and open-key lookups use current locking reads. Aggregate detail locks the period for shared reading; active criterion reads are shared/current reads as well. This matters under MySQL REPEATABLE READ: an earlier actor lookup may already have established an older consistent snapshot before a writer commits. A period's latest version must not be combined with criterion values from that older snapshot. User deletion acquires the same user lock before checking evaluation history.

V44 widens the two audit efficiency columns to DECIMAL(16,4), covering the full supported score range. Apply it through the controlled MySQL upgrade for existing V31 tables. V31 is additive CREATE TABLE IF NOT EXISTS SQL for an existing users prerequisite. It does not enable Flyway or repair the incomplete historical SQL chain. Hibernate schema updates and the SQL file describe the same new mappings. Existing startup repairs and seeding add no evaluation data or permissions. For deployment, reconcile the current schema and use the established controlled schema-update path; the presence of the migration does not prove it ran.

## Frontend and validation

performance.js and performance.css add the profile region; api.js owns the wrappers. Separate request generations and captured user/period/version/form contexts prevent stale navigation or mutation responses from changing another profile. A 409 offers explicit refresh rather than overwriting newer changes. Text/snapshots are escaped; responsive tables scroll within their container. The shared nested-modal close helper clears the main overlay's inert attribute even after standalone confirmations, so the next edit form remains operable. Asset versions in index.html were updated.

Focused tests cover arithmetic, validation, access/ownership, history, closed correction, soft deletion, period copies, account retention, competing creation/edit/rollover and consistency after an older repeatable-read snapshot. The opt-in real-browser smoke covers desktop/mobile, escaping, read-only/hidden views, closed correction, stale-version refresh and delayed profile navigation. It uses a random HTTP port, synthetic users, H2 and target/test-uploads/performance-browser.

The opt-in performance-mysql profile is restricted to the disposable loopback instance at port 13387 and performance_synthetic. The separate migration test uses performance_ddl_synthetic, creates only the users prerequisite, applies V31 repeatedly and validates the mappings using Hibernate with Spring's snake-case naming strategy. Neither test loads the default datasource for connection use. Production migration execution, deployment and actual employee records remain unverified.

Validation on 2026-10-24: the full Maven suite with performance.browser.enabled=true and performance.mysql.enabled=true reported **199 tests, 196 passed, 3 skipped, zero failures/errors**. The three skipped cases belong to the separate existing melt MySQL opt-in suite; the new performance MySQL cases and migration test ran successfully on isolated MySQL 8.0.31. All 34 application scripts and the new browser smoke passed node --check; all 15 existing Node regression tests passed. Chrome desktop/mobile smoke passed, including deletion followed by editing, 409 refresh, owner/manager visibility and delayed navigation. The MySQL REPEATABLE READ snapshot regression is intentionally MySQL-specific because H2 rejects the corresponding locking scenario with a serialization failure. No default datasource, real upload storage or production record was used.
