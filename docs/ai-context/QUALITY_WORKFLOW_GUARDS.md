# Selected quality workflow guards — 3–1, 3–3 and 3–6

This change is independent of the still-open stage-movement PR. It implements only the three selected rules. Role MANAGER is the explicit exception to the pending-report edit lock and two-person final approval; ADMIN is not exempt. Historical tensile movement protection has no manager exception.

## 3–1: one lock for pending RT report data

RadiographyWorkflowGate acquires the open workflow write lock before report or defect mutation, serializing those operations with workflow decisions. In interpreter-editable states, existing assigned-interpreter/manager authorization is retained. In MANAGER_REVIEW, only an enabled MANAGER may edit; all other editors, including ADMIN, are rejected.

The same gate covers report save/delete and defect create/update/delete/welding confirmation. Shared-template create/update/delete checks pending workflows on the definition so adding courses cannot indirectly modify pending reports for a nonmanager. Existing used-template/referential integrity and archive constraints remain; manager exception does not waive them.

GET report synchronization does not append missing template lines during pending review, including manager GETs. A manager can explicitly save to synchronize lines. Manager saves preserve PENDING_MANAGER_APPROVAL and keep workflow state/tasks in review; they do not reopen interpretation or rewrite copied approvals/signatures/snapshots. Manager changes are live-report changes; the existing interpreter snapshot continues to describe what the interpreter previously signed. This is not the unselected report-version redesign (3–2).

The report editor and defect controls reflect the lock/manager exception. Server guards remain authoritative for already-open forms and direct API requests.

## 3–3: separate interpretation and final approval, manager exempt

Final approval compares the current actor ID against the stored interpreter approval identity for the current round. A nonmanager who signed the interpretation cannot sign final approval, even with both permissions or ADMIN role. MANAGER may perform both approvals with their own signature and identity. Being manager does not authorize impersonating a different assigned interpreter.

Manager-task assignment excludes the current interpreter approver unless that candidate is a MANAGER. Workflow permissions now expose canApprove separately from canReview, so the UI can show review/return actions without offering forbidden final approval. A return for correction remains possible; it is not a second approval. The server checks separation under the workflow lock before copying a final signature or mutating tasks/approval records.

## 3–6: saving tensile dispositions and applying movement

SaveTensileDispositionRequest adds optional Boolean applyStageMove:

- Existing disposition edit, omitted or false: update decision data only; no piece movement, stage/substage history, process-date change, scrap reversal or welding-dispatch effect.
- First disposition, omitted: retain prior movement behavior only when this is the latest test on an unarchived piece still in TENSILE. A first decision on a historical pending test only records data.
- Explicit true: operational decision for any test on an unarchived piece currently in TENSILE. Ineligible requests fail before editing the decision, including when the caller is MANAGER.
- Explicit false: record-only, even for a new current decision.

Every disposition form shows Save corrections and Save and transfer. The transfer button is disabled when the piece is archived or outside TENSILE. Only an operational scrap decision opens the scrap details form.

Piece locks serialize test creation and mutation. Mutations lock piece then test; clients omitting the flag on a first decision use an ordered latest-test locking query for a current read on MySQL. Existing threshold evaluation, sample correction, automatic acceptance, signatures, production routing and other permissions remain unchanged. This is not the unselected sample-version/re-review feature (3–5), nor mandatory RT workflow enforcement (3–4).

## Compatibility and validation

No schema/data migration is needed. Existing role and permission codes and API paths stay valid. Legacy clients that edit an existing disposition without applyStageMove now get safe record-only behavior; new-current first decisions keep their prior default.

Updated regressions cover nonmanager CRUD locks, shared-template/read synchronization, manager edit exception, unlock after rejection, both-grant OPERATOR/ADMIN self-approval denial, manager self-approval exception, historical edits by manager/reviewer, explicit-transfer rejection, fresh-current decisions and system-approved corrections. RT coverage is inherited by the existing MySQL workflow suite; the new historical-tensile suite also runs on an isolated owned MySQL schema. Four Node regressions cover edit locks, final approval capability and the two disposition save actions.

Local validation: 29 Node tests pass; Java source parsing passes. Full Java/H2/MySQL execution must be checked in GitHub CI because Maven dependencies are unavailable locally. Browser smoke execution remains unverified locally. No default or production datasource has been started or modified.
