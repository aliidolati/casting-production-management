# Category 4: corrections without deleting and recreating records

Implemented 4–1 through 4–4 independently from draft stage-movement and quality-workflow PRs.

- PATCH /api/notes/{id}: existing PieceNoteRequest; note author needs MANAGE_PIECE_NOTES, MANAGER may correct any note (including legacy notes). Creation author/date stay intact. Archived pieces must be restored first.
- PATCH /api/welding-marks/{id}: existing WeldingMapMarkRequest; MANAGE_WELDING_MAP. Corrects normalized coordinates, catalog/custom defect and note while retaining identity and creation metadata. Archived pieces are protected.
- PATCH /api/heat-treatments/{id}: UpdatePieceHeatTreatmentRequest replaces only per-piece treatmentStage, plate label/HB, details and optional tensileDispositionId. Requires MANAGE_HEAT_TREATMENT_LOG and an unarchived piece. The disposition must belong to that piece. Batch identity/cycle, other members, production stage, process date and production history are untouched. Omitted optional correction fields become null.
- PATCH /api/melt-passports/materials/{id}: existing MeltMaterialRequest; corrects name/category with normalized-name uniqueness. Creation, correction and active-state changes require new MANAGE_MELT_MATERIALS (MANAGER implicitly has it; no automatic grant to existing ADMIN/OPERATOR). Used material IDs and stored material-name snapshots are preserved; no hard-delete route is introduced. Consumers must receive the new explicit grant before using catalog creation.

Successful changing PATCH operations record scalar actor ID/username, target ID/type, timestamp and selected before/after business fields in record_edit_events in the same transaction. No-op edits do not add events. Pessimistic target-row locks serialize edits, so each snapshot represents the previous committed value. These locks do not detect stale forms: a later save may replace an earlier change, which remains visible in history. History is not a signature/version approval system.

GET /api/record-edits/{kind}/{id}?page=0&size=25 is bounded to 100, newest-first with ID tie-breaker. Kinds: notes, welding-marks, heat-treatments, materials. Read requires the corresponding correction permission/ownership or MANAGER. Manager history remains available after deletion; events deliberately have no foreign keys. No history mutation endpoints exist. UI provides correction buttons, selected-field forms and paginated history; untrusted text is escaped. Existing endpoints remain compatible except the deliberately stricter catalog authorization.

## Upgrade and validation

V40__record_edit_history.sql adds one table only, without changing records or grants. This branch does not depend on migrations from other open proposals. JPA, the 50-table/65-FK sanitized MySQL fixture and schema-baseline test agree. Reconcile this additive script with the existing deployment schema procedure: Flyway is not activated and the incomplete chain must not be enabled blindly. No production/default database was started or modified.

Regression coverage includes note ownership/manager/archival, invalid coordinates, retained creation metadata, per-piece heat edits without movement, explicit catalog grants/duplicate names, retained audit after note deletion and migration repeatability/preservation. Full Java/H2 and MySQL results must be checked in CI; Maven Central is unavailable locally. Browser smoke testing is unverified.
