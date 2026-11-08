# Stage movement access

The production UI exposes two manual movement actions.

- **Normal next step** uses `ADVANCE_PIECE_STAGE` and the existing `StageTransitionPolicy.forwardMove` rules, including substages and the process-date rules on `POST /api/pieces/{id}/advance`. Legacy stage and substage routes continue to support their existing normal transfers. They direct non-normal production moves to the correction action.
- **Status correction** uses `CORRECT_PIECE_STAGE` for both single and bulk actions. An active manager has implicit access. An active admin needs the explicit grant. The grant is unavailable to operators and grants neither normal advancement nor operational actions.

## Correction

`GET /api/pieces/{id}/stage-correction/targets` lists valid production stages and substages in both directions. `PATCH /api/pieces/{id}/stage-correction` accepts the selected typed destination. The payload carries a stage and, when applicable, the corresponding substage.

`POST /api/pieces/bulk/stage-correction/targets` accepts `{pieceIds}` and returns the intersection of those pieces' destinations. `PATCH /api/pieces/bulk/stage-correction` accepts `{pieceIds, target}`. The service locks the pieces in ID order, validates all selected destinations and applies every change in one transaction. A stale, unavailable or invalid target for any piece rejects the batch.

Correction records ordinary production history and preserves the outgoing process date. It requires no reason and creates no dates or approvals for locations passed over. Archived pieces and pieces with an open radiography workflow remain blocked. Only MANAGER can correct a piece out of casting, and any active unfinished melt passport must first be approved or cancelled. Welding, scrapping and aging destinations use their operation-specific workflows. A manager can still restore a scrapped piece to a production location. Admin correction grants do not authorize scrap reversal.

## Compatibility

The former stage-skip routes and profile history display are retired. Historical rows and their piece relationships remain in the database. The retired `SKIP_PIECE_STAGE` enum value still loads historical account grants but is omitted from permission lists, inactive for authorization and ignored when saving grants. Existing skip grants confer no correction access. Existing correction grants cover both directions and bulk movement. No schema migration is required.
