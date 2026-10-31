# Piece scrap dispatches

Implemented 2026-10-31. Scrap keeps the existing `SCRAPPED` production stage and all existing movement/workflow/archival authorization gates.

## Storage and lifecycle

`piece_scrap_dispatches` records each transition to SCRAPPED: piece FK, reason code/custom text, optional notes, original sent time/actor, origin stage/substage, last edit time/actor, version and reversal time/actor. A nullable unique `active_piece_key` mirrors the piece ID while active. Return from SCRAPPED closes the active entry; a later scrap creates another entry. Original time/actor are never changed by reason editing. `piece_scrap_dispatch_defects` optionally links same-piece radiography defects; links do not confirm welding or move a radiography workflow.

All reasons use `DefectTypes`, identical to welding, including OTHER with mandatory custom text. Notes are optional (up to 10000 characters); OTHER text is limited to 255. No synthetic reason is assigned to legacy rows. Reports include current SCRAPPED pieces regardless of archival status and show `ثبت نشده` for missing reasons. Available historical scrap time/actor are used without inventing missing metadata. Filling a legacy reason preserves the available original history, with the editing actor/time recorded separately.

## Writes and API

`UpdateStageRequest`, `BulkUpdateStageRequest` and `SaveTensileDispositionRequest` accept nested `scrap: {defectType,defectTypeCustom?,notes?,defectIds?}`. The central `PieceService.applyStageChange` rejects a move to SCRAPPED without a valid reason, including admin/trusted and tensile routes. Stage changes, dispatch records and stage history share one transaction. Bulk moves acquire piece locks in UUID order. A piece write lock, version checks and the unique active key protect reason editing and cycle changes.

`GET /api/pieces/{pieceId}/scrap-dispatches` exposes cycle history. `PATCH /api/pieces/{pieceId}/scrap-dispatch` edits the CURRENT active reason with the original reason fields plus `version`; missing/stale versions produce 409 for existing entries. An entry-less legacy scrap accepts no version. Writes require MOVE_PIECE_STAGE (or ADMIN), current SCRAPPED state, and an unarchived piece, matching welding's archival rule. Archived entries remain readable. Repeated SCRAP tensile disposition saves edit the active reason with its version rather than creating duplicate cycles.

`GET /api/dashboard/scrap-report?projectId=...` requires VIEW_DASHBOARD (or ADMIN). It returns one row per current SCRAPPED piece, including archived pieces. Restored-to-production pieces leave this report but their cycles remain in piece history. The dashboard tab follows welding's project filter, row-to-profile navigation and UTF-8 BOM CSV export, with extra origin/archive columns.

## UI

`scrap.js` provides the shared nested reason form, optional same-piece defect selection and editable profile history. Single/dropdown, profile, admin jump, bulk and tensile paths collect a reason before submitting. Cancelling by button, backdrop, Escape or X cancels the pending operation. The reason catalog is reused directly from radiography/welding helpers, not copied. No frontend API call alone supplies a default reason.

## Schema and validation

V37 adds only two new tables and does not backfill or rewrite old pieces/history. The reviewed synthetic schema fixture extends the original 46-table/62-FK inspection with these tables (48 tables/65 FKs). Runtime Hibernate schema update and existing startup repairs remain as configured; this feature does not enable Flyway's incomplete historical chain. V37 must be reviewed/applied through the deployment's schema process; its presence does not prove production execution.

Tests: ScrapIntegrationTest and its opt-in ScrapMysqlIntegrationTest subclass cover catalog validation, optional notes, trusted/admin gates, ownership/permissions, edits/version conflict, archived/legacy reporting, repeated cycles, tensile and atomic bulk rollback. ScrapMigrationMysqlTest checks additive/repeatable DDL, preserved legacy rows, FK enforcement and unique active cycles. ScrapBrowserSmokeTest enables the real HTTP/H2/Chromium script with `-Dscrap.browser.enabled=true`; it covers cancel, Other validation, notes, edit persistence, original timestamp, archived/legacy report, CSV and desktop/mobile. Browser toolchain: Playwright and Chromium (`CHROME_BIN` may override `/usr/bin/chromium`). All data/uploads are synthetic and isolated.

Validated in this cloud instance: full fast suite 315 discovered / 246 executed / 69 optional MySQL/browser skipped, no failures/errors; full MySQL suite 66 executed, no skips/failures/errors; enabled ScrapBrowserSmokeTest passed, including admin jump and tensile UI; 14 relevant Node regressions and edited-script syntax checks passed. Final package build passed. Testcontainers cleanup left no running containers. Production datasource and deployment were not accessed or changed.
