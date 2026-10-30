# Cloud backend tests and private database backup

Prepared 2026-10-30. See `MYSQL_SCHEMA_INVENTORY.md` for the inspected structural inventory and `DATABASE_STRUCTURE.md` for schema ownership.

## Source database findings and backup

The explicitly authorized inspection used only MySQL metadata SELECTs, the Flyway history SELECT, and a logical `mysqldump`. Spring was never started against the source. No source database DDL/DML, deletion, restore, user/grant change or schema migration was performed.

- Source server: MySQL 8.0.31, 46 InnoDB base tables, 45 primary-key constraints, 27 unique constraints, 62 foreign keys and 9 CHECK constraints. No triggers/routines/events were found with the inspected account's metadata visibility.
- The existing Flyway history has one successful **BASELINE version 22** row. This is evidence of baselining, not evidence that numbered migrations were applied.
- The current runtime still uses Hibernate `ddl-auto=update` and startup repair components. Flyway core/MySQL libraries are present, but Boot's Flyway integration/starter is absent. The SQL chain is incomplete (`tasks` is referenced without a preceding table creation). This task does not activate Flyway or change runtime schema ownership.
- The real logical backup, schema-only private dump, metadata and SHA-256 files were saved in a private sibling directory on the user's desktop, **outside this source tree**. Its exact location is provided to the user separately. No production rows were copied into this repository.
- Dump completion and checksums were checked. A full restore of the **real-data backup** has not been tested. `--single-transaction --skip-lock-tables` captures InnoDB data consistently, but concurrent DDL must be avoided during an export. It is a database backup, not a backup of uploaded drawings/signatures or an encrypted offsite copy. Retain it privately.

`scripts/backup-mysql.mjs` is an explicit read-only utility, never called by tests/CI. It requires a new destination outside the repository. Set the three `SPRING_DATASOURCE_*` environment variables privately; `MYSQL_BIN` names the directory containing `mysql` and `mysqldump` (on Linux set it explicitly). Credentials are passed via a child-process environment, not CLI arguments. It suppresses raw client errors. The utility does not restore, initialize or modify a source database. Review destination ACLs/encryption according to local policy; POSIX modes alone do not enforce Windows ACLs.

## Sanitized structural fixture

`src/test/resources/db/mysql/schema-synthetic.sql` includes the 46 reviewed CREATE TABLE statements only. Source database names/addresses, accounts, records, data-derived counters, dump headers, DROP statements and source migration-history records are excluded. The structure retains column types/nullability, collations, indexes, FK actions and CHECK expressions. There are no source users, password hashes, names, production measurements, copied audit snapshots, signatures or file contents.

`SchemaBaselineMysqlIntegrationTest` imports that fixture into an **empty, owned synthetic schema**, re-enables FK checks, runs Hibernate **validate**, runs the application's startup repairs, then inherits the melt assignment/approval API regressions with synthetic users/pieces. Startup repairs may reconcile that disposable database; they never see the source datasource. The history table remains empty. This proves neither a complete production migration chain nor a real-backup restore.

Other MySQL Spring contexts bootstrap current mappings with Hibernate `create-only`; standalone migration tests create only their synthetic prerequisites. The five additive migration regressions exercise V30, V31, V32, V34 and V36, including repeat execution and preserved legacy synthetic values. Every new schema change must update the relevant fixture/tests after review; do not regenerate the fixture blindly from production.

## Codex Cloud setup

1. Publish the reviewed **source only** to a private GitHub repository and connect that repository to a Codex Cloud environment. This local source directory was not a Git working tree at preparation time; no repository, commit, remote, upload or Cloud environment was created by this task.
2. Choose/provide JDK **17 or newer**, Bash and network access for Maven dependency downloads during setup. The project has a Maven wrapper; no Node/frontend build is required for backend tests.
3. Configure the environment's setup script as `bash scripts/codex-setup.sh`. Use the same command as a maintenance script if dependency changes must refresh a cached environment. It resolves fast/MySQL test dependencies and compiles tests; it never starts the runtime application or accesses a database.
4. Run `bash scripts/test-backend.sh fast` during development. For a warmed cache with agent internet disabled, use `bash mvnw -o -B -ntp test`. If offline resolution reports missing artifacts, refresh setup with network access rather than adding production access.
5. Do not supply production datasource credentials, JWT keys, exports or uploads to Cloud or Actions. Tests have their own H2 configuration and synthetic bootstrap password. Testcontainers generates MySQL credentials per JVM.

Official environment documentation describes a separate setup shell, cached environments, dependency network access during setup and optional maintenance scripts: [Codex Cloud environment documentation](https://learn.chatgpt.com/docs/environments/cloud-environment). Do not rely on `export` in setup persisting into the agent phase. The documented page does not guarantee a Docker daemon; check `docker info` in the actual environment.

## Commands and execution locations

| Purpose | Command | Requirements |
|---|---|---|
| Unit + Spring/H2 API/security/concurrency tests | `bash scripts/test-backend.sh fast` | Cloud/local/Actions; JDK 17+, Maven dependencies |
| Same fast suite offline | `bash mvnw -o -B -ntp test` | Previously populated Maven cache |
| MySQL API, inspected-schema and upgrade regressions | `bash scripts/test-backend.sh mysql` | Working Docker daemon and access to pull MySQL/Ryuk images; default CI route |
| MySQL Maven command directly | `bash mvnw -B -ntp -Pmysql-it -DskipUnitTests=true verify` | Same Docker requirements; Failsafe runs at verify |
| Optional native MySQL fallback | `bash mvnw -B -ntp -Pmysql-it -DskipUnitTests=true -Dmysql.tests.runtime=native -Dmysql.native.bin=/absolute/path/to/mysqld verify` | Installed **MySQL 8.0** binary; permission to initialize/start an owned process |

The native fallback is explicitly opt-in. It initializes a new random datadir under ignored `target/`, starts a new loopback process on a random port with `--no-defaults` and X protocol disabled, assigns random test credentials, and stops only that owned process on JVM shutdown. It accepts no external URL, existing datadir or system-service fallback. Retained local logs/data contain synthetic fixtures only; they are not source artifacts. This path also lets a Cloud environment run MySQL if an appropriate binary/process facility is actually available. It is not a substitute claim that Testcontainers was run.

The GitHub-hosted workflow `.github/workflows/backend-tests.yml` has independent `fast` and `mysql` jobs on Ubuntu 24.04/JDK 17. The latter runs Testcontainers and fails if Docker/images are unavailable; it does not skip silently. No repository secrets or external MySQL service are required. It runs on pushes, pull requests and manual dispatch after the files are actually pushed and Actions is enabled. The laptop can be off.

MySQL coverage includes candidate mixed collations, real MySQL time types, performance approval/correction/ownership/concurrency and repeatable-read behavior, production tracking, radiography approval/snapshots and the inspected-schema melt workflow. MySQL-specific tests require the **Actions mysql job in the ordinary Cloud environment when Docker/native MySQL is absent**; they can also run anywhere with the stated runtime. H2 tests cannot validate MySQL DDL, enum CHECKs, collation errors or MySQL isolation semantics.

Optional browser suites remain opt-in and require their own Chrome/Node/browser automation setup; this backend workflow does not enable them. No browser assets were edited here.

## Runtime configuration after removing source secrets

`src/main/resources/application.properties` now contains placeholders for `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET`. The current local runtime configuration was preserved in a private properties file beside the real backup before removing the source literals. A local runtime launch may load that private file using `--spring.config.additional-location=file:/absolute/private/path/application-local-private.properties`. Do not put it back in the repository. Keep the existing signing key stable unless explicitly rotating it; no application restart/key rotation was performed here.

Only an **empty** runtime database additionally requires an explicit `APP_BOOTSTRAP_ADMIN_PASSWORD`; existing users and permission choices are preserved. The seeder no longer contains a default password and never prints it. This change has focused seeding regressions. An already distributed secret may remain in old JARs, local generated outputs or any pre-existing remote history; this task does not claim to erase those or rotate live credentials. Do not distribute old build artifacts.

## Validation

- Full fast Maven suite: **293 discovered, 236 executed, 57 optional MySQL/browser cases skipped; zero failures/errors**. This includes JWT external-configuration and empty/existing-database bootstrap/permission regressions.
- MySQL Failsafe suite on a newly initialized **owned native MySQL 8.0.31**: **55 executed, zero skipped/failures/errors**. This includes inspected-schema import/JPA validation, inherited API workflows, standalone additive upgrades and the isolation-name guard. A focused native lifecycle rerun passed after adding graceful shutdown/Windows console handling.
- Local Java was **25.0.3**, compilation target 17. The GitHub workflow selects JDK 17; execution on that hosted JDK has not occurred here.
- The exact `bash scripts/codex-setup.sh` command completed successfully locally (Git Bash/Maven wrapper): dependency warm-up and test compilation passed. The final lifecycle check found **zero** owned synthetic MySQL processes remaining.
- `node --check scripts/backup-mysql.mjs` and `bash -n` for both shell scripts passed. Private export checksums match. Isolated Git ignore checks confirm that private exports, local properties, `.env`, uploads, scratch files and target outputs are ignored while the structural fixture/migrations remain publishable.
- No browser suite was enabled because no UI code changed. No source database data/schema was modified. The real backup was not restored or uploaded.

Docker was unavailable locally. **Testcontainers itself and GitHub Actions were not executed**; the native run exercises the MySQL test bodies and schema behavior, but does not validate Docker image startup, Ryuk cleanup, hosted permissions or the remote workflow. Those checks must pass in the Actions `mysql` job after publishing the reviewed source. Do not infer them from native MySQL/H2 success.
