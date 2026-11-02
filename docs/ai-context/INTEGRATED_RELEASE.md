# Integrated approved access changes

Combines approved PRs #2 (2–1/2–2), #3 (3–1/3–3/3–6) and #4 (4–1 through 4–4) with category 5 already in main. Original branch commits are preserved as ancestors; no other proposed features are enabled.

Resolution retains all advance/skip/catalog/cancellation/batch permissions, all API wrappers, both per-piece heat correction and shared-cycle preview controls, RT manager exceptions and record-only historical tensile edits. All modified script URLs are refreshed together. Synthetic fixture totals are 52 tables and 66 foreign keys: main's heat_batch_events plus piece_stage_skips and record_edit_events.

Upgrade requires reviewing/applying additive V39, V40 and V41 through the installation's existing schema process, alongside the already-required V38 manager upgrade. Existing users need explicit selection of the new grants (MANAGER has implicit access). Flyway remains disabled; do not run the incomplete chain blindly. Original audit and workflow documents below are historical per-branch descriptions; these combined counts supersede their per-branch fixture totals.

Local integration verification: 39 Node regressions pass; all 37 application scripts pass syntax checks; script references exist and are unique. Full Java/H2 and MySQL/migration suites must pass on this combined tree before merge. Browser smoke testing remains unverified. No production/default datasource has been used.
