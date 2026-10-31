# Frontend architecture

Update 2026-10-31: scrap.js shares the welding reason catalog, collects optional notes and supports active reason editing. Profile/dropdown/bulk/admin/tensile paths use the same form; the dashboard scrap tab includes archived pieces and CSV. See [scrap](SCRAP.md).

Update 2026-10-31: radiography chain display isolates each step with `bdi` inside an LTR span so Persian defect names (including ابعادی) cannot reorder the welding step. The shared HTML formatter escapes all step text and is used in the editor preview, report/print chips and piece defect list. Plain-text formatting and stored records/snapshots are unchanged. Focused browser regression: `node src/test/js/radiography-order.browser.cjs` (Playwright and Chromium; `CHROME_BIN` may override the executable).

Update 2026-10-30 (security): project code/name and definition pieceName are escaped when building edit input values, preserving the original text without interpreting stored markup. projects.js?v=65 and auth.js?v=73 invalidate the matching cached scripts. Successful own-password changes clear the local session immediately and show the login form; remote resets are handled by the existing 401 flow. Chrome regression covers all three stored-text sinks, editing/round trips, desktop/mobile and both password-change/reset flows. See [security fix notes](../audits/2026-10-30-security-fixes-fa.md).

Update 2026-10-30: the melt form adds ADMIN operator selection/save-and-assign; task detail routes MELT_PASSPORT_ENTRY/APPROVAL to the melt form instead of the radiography API. Both have localized badges, and pooled review assignees display as a group without a profile link. Workflow actions refresh the task detail. New-cycle detection uses statuses, independent of the reader's editable flag. Matching script cache versions were updated. See [melt passports](MELT_PASSPORTS.md).

Update 2026-10-29 (text positions/order): report position inputs and the standalone location input are text fields capped at 255 characters. Exact trimmed-string comparison replaces Number/parseInt coercion for point/range formatting, duplicate detection and editing; old numeric snapshot values are supported by String conversion. Text labels are escaped in position badges, previews, defect chips and welding labels. Numeric welding locations retain the cm suffix; nonnumeric labels do not receive a unit. With welded+reshootOk, text and the visual preview place reshoot before welding. Asset cache versions were incremented. Browser smoke covers L4 points and L5-L8 ranges through edit/reload/print and mobile.

Update 2026-10-29: radiography.js owns the shared 13-type-plus-OTHER browser defect catalog; radiography-report.js now reuses it for both defect selectors and explicit custom-text fields. Dashboard filters no longer duplicate a stale fallback list. The report's optional «ثبت نتیجه بررسی مجدد» section selects either a second defect or reshoot-OK and supports removal. Draft/payload selection is exclusive, and previews/report chips/printing render «ریشوت → OK». THICKNESS_MEASUREMENT is displayed as «ضخامت مجاز» while preserving its API code. Matching index.html asset versions were bumped.

Update 2026-10-26: the magnesium grid is LTR in desktop/mobile layouts, with native keyboard order Mg, T_rotary, T_610, T_ar, B, w. Approved passports retain editable=false and expose a separate server-derived correctable flag. ADMIN enters an explicit correction mode: fixed piece membership, editable business fields/materials/checks, optional reason, cancel and save/atomic calculate-and-save; no resubmission button. Audit changes render escaped values with Persian field labels in a contained scrolling table. Correction errors preserve the form/version/reason. MeltPassportAPI.correct owns the new endpoint; matching asset versions were updated.

Update 2026-10-24: performance.js/performance.css mount an independent evaluation region from profile.js, only for owner/ADMIN. PerformanceAPI in api.js owns period/criterion/history calls. Independent request generations protect profile and period navigation; mutations capture their original version and offer refresh on 409. The region includes weighted totals, a zero-to-one result, manual rollover, closed-period correction and paginated audit history. See [employee performance](EMPLOYEE_PERFORMANCE.md). Matching asset versions were updated.

Update 2026-10-24: both tensile dashboard reports offer UTF-8 BOM CSV downloads from the last successful report. Quality CSV applies the current YS/EL range and includes Q/band; trend CSV includes every returned sample with its test date and UTS/YS/EL. Stage report rows show localized substages and export a separate substage column. The welding profile reuses the dispatch form to edit the active dispatch type/custom text, notes and linked open defects; it refreshes the profile after saving through `PieceAPI.updateWeldingDispatch`. Matching script cache versions were updated.

Update 2026-10-22: the melt register now opens from Dashboard Reports; piece profiles have an SVG melt icon with count/latest status and a history selector. Melt forms use compact responsive controls. Errors/warnings use six-second top toasts; the shared toast helper accepts optional position/duration and cancels its earlier timeout, while existing callers keep their defaults. API errors now retain status/field-error metadata. Obsolete melt requests do not overwrite or reopen another view. See [melt passports](MELT_PASSPORTS.md) for permissions and validation.

Update 2026-10-19: `melt-passport.js`/`melt-passport.css` add the shared piece-profile form, review register, material catalog activation and consumption report. `api.js` owns all HTTP wrappers. The script loads after existing profile/workflow helpers; dates remain Jalali in the interface and ISO in API contracts. See [melt passports](MELT_PASSPORTS.md).

Reviewed: 2026-10-16. Root: `src/main/resources/static/`. Findings are from source inspection; no interactive browser session was run.

## Runtime and loading

`index.html` is a Persian `lang="fa"`, `dir="rtl"` application shell with login, navigation, content sections, tables, and shared/nested modal containers. Spring serves it from the same application as the REST API. There is no frontend `package.json`, transpiler, bundler, component framework, or frontend test runner in the inspected tree.

Scripts are classic scripts sharing one browser global scope. They load in explicit order in [index.html](../../src/main/resources/static/index.html):

1. `theme-definitions.js` and `theme-manager.js` in the head.
2. Jalali/date-picker helpers, `api.js`, `auth.js`, and `app.js`.
3. Dashboard, projects, pieces, radiography/report, heat log, machining, welding, notes, maps, tensile/reporting, production-flow and tracking scripts.
4. Notifications/users, task common/list/detail/profile/workflow scripts, task router, and theme selector.

Some functions refer to globals declared later and are safe only because calls occur after loading. Moving scripts, introducing module scope, or adding duplicate global names can break unrelated screens. `auth.js` starts authentication on DOM readiness; `app.js` registers navigation/modal listeners and lets auth choose the initial section.

`StaticResourceCacheConfig` caches `/js/**`, `/css/**`, `/fonts/**` publicly for seven days. HTML/configuration uses revalidation. Script and stylesheet URLs carry manually maintained `?v=` values; updating an asset requires updating its matching version in `index.html`. No service worker was found.

## State and navigation

- `AppState` in `app.js` tracks current project/definition context.
- `AuthState` in `auth.js` holds current user and permissions; `hasPermission` includes an administrator override.
- Individual modules own mutable globals such as `TrackingState`, dashboard/report state, task state, and `NotificationCenterState`.
- Most navigation toggles `.content-section.active` and updates buttons. Project/definition/piece context is maintained in memory rather than a universal URL router.
- `task-router.js` handles `#/tasks/my`, `#/tasks`, `#/tasks/{uuid}`, and `#/profile/{uuid}`, including optional query filters. It handles hash changes and supports deep links after authentication. Other navigation clears the task hash.
- `app.js` provides modals, nested dialogs, focus trapping/restoration, background `inert`, toast messages, localized date formatting, escaping and loading helpers.

## API contract

Evidence: [api.js](../../src/main/resources/static/js/api.js), [auth.js](../../src/main/resources/static/js/auth.js), [PageResponse](../../src/main/java/com/factory/productmanager/model/dto/PageResponse.java).

- API base is `${window.location.origin}/api`; endpoint wrappers are grouped into objects such as `ProjectAPI`, `PieceDefinitionAPI`, `PieceAPI`, task/workflow/report APIs.
- `apiRequest` serializes JSON and adds the JWT Authorization header. `apiUpload` lets the browser construct multipart boundaries. `apiBlob` downloads authenticated binary resources, including drawings.
- A 401 dispatches `auth:unauthorized` and ends the client session, except credential-check endpoints (login and current-password validation), which surface their server message.
- 204 maps to `null`. Other JSON operations expect JSON and throw `Error` from the server's message or first validation error. Structured field errors/status are generally reduced to a message.
- Paginated APIs return `{items,totalElements,page,size,totalPages}` with zero-based pages. Several legacy list APIs still return arrays; do not interchange the shapes.
- `PieceResponse` supplies allowed next stages/substages and a `forwardMove` with required permission/date information. These are graph/admin-role hints, not a complete authorization result: ordinary permission checks, archival and open-workflow guards are applied separately. UI selection helpers consume these fields; the server remains responsible for enforcing moves.
- The two synchronized tracking search inputs share `q`: numeric-only input matches an exact piece number; text remains a partial keyword search. Dedicated name/project column filters retain partial matching, including numeric text. Entering a search preserves the selected stage/substage/date/column filters and resets only pagination.
- Tensile rows render nullable `tensileTrackingInfo` as a decision label and separate duration boxes, all using the tensile stage color and distinct from actual substages. Each recorded non-null aging duration for the current repeat cycle appears in its own minute box, even when the latest decision is NEXT_TENSILE. The dashboard scrap-count card reads existing `scrappedInPeriod`, whose legacy name represents current unarchived SCRAPPED count scoped by project, independent of period filters.
- The profile production rail keeps the fixed production route order. Existing process dates appear on the corresponding locations and do not reorder the route; history APIs retain their established recorded-time order.
- JWT and cached user metadata are stored in localStorage (`pm_auth_token`, `pm_auth_user`). Bootstrapping calls `/auth/me` before displaying the app. Logging out clears local storage and notification polling.

## Feature ownership

| Scripts | UI responsibilities and dependencies |
|---|---|
| `projects.js`, `pieces.js` | Catalog CRUD, project/definition/piece detail, drawings/thresholds, piece batch creation, archive/restore, row status output; use `AppState` and shared modals |
| `tracking.js` | Stage cards, filtered/paginated pieces, selection/bulk changes, piece profile and movement/date actions; composes inspection/heat/tensile/welding panels |
| `production-flow.js` | Visual production flow and movement presentation; depends on piece state and movement metadata |
| `radiography.js` | Defect chain editing/display, positions and welding-related interpretation |
| `radiography-report.js` | Template/course editor, report form, results, defect associations and browser print layout |
| `radiography-workflow.js` | Interpreter queue/assignment, workflow actions and signed-version document UI; integrates task/piece/report screens |
| `heat-treatment.js`, `rough-machining.js` | Substage labels/helpers; these are small helper files, not separate applications |
| `heat-treatment-log.js` | Furnace batch lookup/suggestions, per-piece operation records, cycle fields, batch detail/edit |
| `welding-station.js`, `welding-map.js` | Dispatch/completion and defects; authenticated drawing overlay with percentage coordinates, per-piece and aggregated marks |
| `tensile.js` | Multi-sample test entry/edit, thresholds, pending/manual review and disposition |
| `dashboard.js` | Operational overview, alerts, reports, filtering and CSV exports; also defines CSV helper reused by pieces |
| `tensile-quality-report.js` | Canvas quality scatter plot, filters/hover/theme handling; backend supplies point classifications |
| `tensile-trend-report.js` | Canvas UTS/YS/EL charts, fitted/moving-average/outlier series and server statistics |
| `tasks-common.js`, `tasks.js`, `task-detail.js` | Shared task formatting/lookups, lists/forms, task actions, comments, checklist/activity tabs |
| `profile.js`, `users.js` | Profile/task statistics/signature, administrator user/permission management |
| `notes.js`, `notifications.js` | Piece notes; notification bell/feed/read/dismiss and 45-second polling |
| `jalali.js`, `persian-datepicker.js` | Gregorian/Jalali conversion and date inputs; API dates remain ISO values |
| `theme-definitions.js`, `theme-manager.js`, `theme-selector.js` | Persisted theme choice, CSS tokens, `themechange` events; Canvas charts redraw when theme changes |

There is also `_flow-preview.html`, an auxiliary static preview file. It is not the production application entry point.

## Rendering, styling and reports

- HTML strings and `innerHTML` are used extensively; `escapeHtml` in `app.js` escapes user text and attribute values. Treat text escaping and URL/context validation as separate concerns.
- CSS is split between `tokens.css`, large `style.css`, font declarations, theme selector, tasks and radiography-workflow styles. Local Vazirmatn WOFF2 files avoid a font CDN dependency.
- Icons and small diagrams use inline SVG; quality/trend charts use native Canvas. No Chart.js or other chart library is evidenced by the loaded scripts.
- Report printing uses browser print behavior, not a server PDF renderer. CSV exports are constructed in JavaScript with a UTF-8 BOM. Signed radiography documents obtain stored report snapshots and approval signatures through the API.
- The profile production rail keeps its oldest-to-newest direction but orders performed positions by their persisted process dates, with recording timestamp and original position as tie breakers. Start/planned positions and undated position order are preserved. It remains one node per stage/substage rather than a per-visit event timeline.
- `tensile-quality-report.js` repeats the nominal Q boundary equation for chart drawing; numerical classification is implemented in backend `TensileQualityIndex`. Keep these synchronized if the business formula changes.

## Risks and development rules

1. **Global coupling:** changing a shared helper, `PieceResponse`, modal DOM ID, or script order can affect many screens. `pieces.js` can reuse a CSV helper defined in `dashboard.js`, illustrating cross-feature dependencies.
2. **Authentication display versus enforcement:** hidden buttons are convenience controls. Server checks differ across tasks, workflow decisions and legacy inspection APIs; do not infer authority from browser state.
3. **XSS/session exposure:** localStorage tokens make unescaped dynamic content consequential. The presence of `escapeHtml` does not establish that every interpolation is safe; this review was not a complete XSS audit.
4. **Asynchronous state:** tracking has a sequence guard (`loadSeq`) and search debouncing. This pattern is not universal; test stale responses, rapid navigation, selection and modal refreshes when changing affected screens.
5. **Date semantics:** preserve ISO API values, Jalali display, date-only process dates, and timezone-free backend date-times. Browser date parsing and JVM timezone can shift displayed boundaries.
6. **Manual cache versions:** a source change alone may leave returning users with cached scripts/styles. Update the exact resource URL version when implementing an asset change.
7. **No automated browser coverage found:** Java tests do not validate DOM behavior, print layouts, charts, RTL, keyboard focus, or asset caching. Use the manual scenarios in [development guidelines](DEVELOPMENT_GUIDELINES.md) for future UI changes.

JavaScript syntax checks during this review are recorded in the development document. No UI behavior or frontend code was changed.
