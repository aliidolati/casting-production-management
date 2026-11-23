# Spectrometry

Spectrometry records are optional laboratory observations. Each result references an existing piece, including archived pieces, and stores the test date and all eight measured values in wt%. A zero is a valid result. The form requires a piece, date and all eight values. It accepts Persian and Arabic digits as well as a decimal point or Arabic decimal separator. Each element is validated independently from 0 through 100, with up to ten fractional digits. Repeated dates and repeated tests of the same piece are allowed.

## Storage and upgrade

- V47 adds `spectrometry_tests` and `spectrometry_batch_receipts`. The first table links to `pieces.id` and indexes test date and piece/date. It uses `DECIMAL(13,10)`, creation/update timestamps and an optimistic record version. Values are serialized as decimal strings.
- A batch is atomic. The caller generates a UUID before submission, then resends the same UUID and payload on a transport retry. The account-scoped receipt records a fingerprint and the original insert count. The account lock serializes concurrent retries, and the unique key prevents duplicate receipts. Receipts remain after individual test deletion so late retries cannot recreate deleted tests.
- Tests survive piece archiving. Project/definition deletion previews list associated tests as blocking references, and the foreign key enforces the same rule for concurrent writes.
- The independent V47 script can be applied to an existing MySQL schema using the project's deployment process. The application's existing `ddl-auto=update` configuration can also create the new mapped tables. V47 does not require activation of the historical Flyway chain. The reviewed synthetic fixture incorporates both tables.

## API

All paths below are under `/api/spectrometry`. The Manager has all four grants. The existing account editor can delegate them independently. Users with the view grant alone can open Spectrometry from the main navigation.

| Method and path | Permission | Purpose |
|---|---|---|
| GET `/` | `VIEW_SPECTROMETRY` | Paged results, default 50 rows, sorted by test date and ID |
| GET `/points` | `VIEW_SPECTROMETRY` | Compact Mg/Fe/Ti projection for all matched results in ascending date order |
| GET `/csv` | `VIEW_SPECTROMETRY` | All matched results in the requested sort order, with ISO dates and precise decimals |
| GET `/pieces?definitionId=…` | Create or edit grant | Compact piece number list, including archived pieces |
| POST `/batch` | `CREATE_SPECTROMETRY_TEST` | Atomic insert of `{requestId, rows}`. New request returns 201, replay returns 200 and changed payload with the same key returns 409 |
| PUT `/{id}` | `EDIT_SPECTROMETRY_TEST` | Replace the measurements, date and piece with `{version, result}` |
| DELETE `/{id}?version=…` | `DELETE_SPECTROMETRY_TEST` | Remove a result using its current version |

The history, point and CSV routes share `projectCode`, `pieceName`, exact `pieceNumber`, and inclusive ISO `dateFrom` / `dateTo` filters. The history route accepts `page`, `size` and `ascending`. The CSV route accepts `ascending`. A result input contains `pieceId`, ISO `testDate`, and `al, si, mg, cu, fe, mn, zn, ti`. Updates and deletes return 204 on success and 409 on a stale version.

## Browser analysis

The three Canvas scatter plots reuse the tensile trend theme and time-domain helpers. All matching observations remain visible with either statistical basis. Checkboxes and point clicks control the same in-memory set of test IDs. For overlapping points, the chart offers a list of the individual results at that location. Paging and filtering retain selection, and leaving the page clears it.

The browser computes an arithmetic mean and least-squares regression on centered elapsed days and centered values, with the first analyzed day as the time origin. Duplicate dates contribute one term for each result. A single distinct date yields a mean and an undefined fit. A constant series with multiple dates yields zero slope and undefined R². R² expresses fit and two-observation fits carry a sample-size note. The line is drawn over the analyzed time span. Regression and displayed statistics use floating-point arithmetic. Records, tooltips, and CSV preserve the original decimal value.

The provisional absolute slope thresholds for the descriptive stable band are 0.0002 for Mg, 0.0001 for Fe and 0.00005 for Ti, in wt% per day. These thresholds are analytical settings in `spectrometry-charts.js`. The feature uses no alloy conformance criteria and selection is independent of workflow status.

## Verification

- `bash mvnw -B -ntp -Dtest=SpectrometryIntegrationTest test` exercises the API and persistence on H2.
- `bash mvnw -B -ntp -Pmysql-it -DskipUnitTests=true -Dit.test=SpectrometryMysqlIntegrationTest,SchemaBaselineMysqlIntegrationTest verify` exercises the API, V47 upgrade and baseline on owned synthetic MySQL.
- `node --test src/test/js/spectrometry-stats.test.cjs` exercises numerical cases.
- `node src/test/js/spectrometry.browser.cjs` checks the browser using Playwright and synthetic API fixtures. Set `CHROME_BIN` to use an existing Chromium executable.
