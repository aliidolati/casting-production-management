# Stage movement access — selected items 2–1 and 2–2

Only normal forward progression and permissioned forward skipping are implemented in this change. No new rollback, scrap/reversal, welding, archive/restore, date-correction or bulk-operation permission is introduced. Existing guards for those operations remain in place.

## Grants

- `ADVANCE_PIECE_STAGE` (پیشرفت عادی مرحله) authorizes the one normal next location, including rough-machining, radiography and heat-treatment substages. `/advance` retains its process-date and workflow rules. Legacy single/bulk PATCH routes also require this grant when they perform a normal forward step.
- `SKIP_PIECE_STAGE` authorizes an explicit forward skip only for an enabled ADMIN. MANAGER has both grants implicitly. OPERATOR cannot skip even if the code is manually present in its stored grants; account administration rejects granting it to OPERATOR and the editor disables that checkbox.
- A skip grant does not grant ordinary progression, scrap, reverse movement, welding or date edits. An advance grant does not grant those operations either.
- Legacy permission codes are retained. Existing users are not automatically granted/regranted ADVANCE or SKIP. After upgrade, the manager must explicitly choose normal-advance access for existing users. The new-user operator form includes ADVANCE in its defaults. Legacy MOVE/UPDATE permissions no longer imply normal progression.

## APIs

- `POST /api/pieces/{id}/advance`: requires ADVANCE and only performs the policy's next step.
- `GET /api/pieces/{id}/skip-targets`: enabled manager/permitted admin only; returns typed later locations excluding the immediate normal next location, current/earlier locations, welding, scraps and aging decision branches.
- `POST /api/pieces/{id}/skip`: accepts `{stage, radiographySubStage?, heatTreatmentSubStage?, roughMachiningSubStage?, reason}`. Reason is mandatory (1–1000 characters, trimmed before storage). Substage must belong to the destination. The server recomputes targets under the piece write lock; client lists are not trusted.
- `GET /api/pieces/{id}/stage-skips`: existing authenticated piece visibility, immutable skip history with actor ID/name, time, origin, destination and reason. No edit/delete audit API exists.

Skip mutation, ordinary history and skip audit insert share one transaction. Cross-stage destination and substage are applied atomically. Current archive, open radiography workflow and casting-departure approval gates apply; permissions never disable those gates. A skip is an explicit administrative movement and does not create tensile decisions, report approvals, signatures, heat operations or dates for omitted locations.

Substage ordering uses the production route, not heat enum ordinal: STAGE_1 → STAGE_2 → CNC_SAMPLE. Aging stages 3–5 remain decision-driven branches and are not offered as skip destinations. Welding/scrapped/delivered origins have no forward skip targets.

Legacy main/substage PATCH routes reject forward skips for everyone, including MANAGER, so a caller cannot evade the skip reason by choosing an older API. Bulk normal calls validate every candidate before mutation and use the same independent grant. Skipping remains a single-piece action; this change does not introduce a new bulk privilege or a separate bulk-skip workflow.

Existing manager backward corrections retain legacy authority. Optional typed substages on UpdateStageRequest preserve the existing cross-stage backward correction in one request, avoiding a partially applied correction followed by a now-forbidden forward-substage request. This is compatibility behavior, not a new rollback grant.

## UI

The profile's normal-next button follows the server's ADVANCE requirement. Eligible admins/managers get a separate skip form with server-provided destinations and mandatory reason. Recorded skips appear in the profile with escaped reason/actor text. The existing manager correction action remains distinct. Bulk normal controls recognize ADVANCE while date/weld/scrap controls continue using their original checks.

## Schema and upgrade

V39 adds only `piece_stage_skips`; historical production rows are preserved. The table has one FK to pieces, scalar actor identities that survive user deletion, and no copied credentials. The record-free MySQL fixture contains 50 tables/66 FKs after this addition. JPA keeps role and permission storage as VARCHAR for additive enum values. Do not enable the incomplete Flyway chain blindly; use the installation's reviewed schema process. No production/default datasource was started or changed during implementation.

## Validation

Added H2/API and inherited isolated-MySQL regressions cover grant separation, direct API/bulk bypasses, operator rejection, mandatory reasons, typed destinations, ordinary substage progression, archived/casting/workflow guards, audit snapshots and backward-correction compatibility. A standalone owned-MySQL V39 regression checks repeatability and preservation of the prior piece. Unit tests cover route ordering and terminal origins. Three Node regressions cover independent role/grant controls, the atomic reason form and escaped skip history.

Locally: all 28 Node regressions pass; Java source parsing has no syntax errors. Local Maven remains unavailable because its dependencies are absent and Maven Central cannot resolve. Full Java/H2/MySQL execution must be verified by GitHub CI on this PR; parsing is not compilation. Browser execution remains unverified locally because Chromium is unavailable.
