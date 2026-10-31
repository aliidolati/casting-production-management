# Inspected local MySQL structure — 2026-10-30

Update 2026-10-31: Feature extension (not a new production inspection): V37 adds two scrap tables and three FKs; the synthetic fixture totals 48 tables/65 FKs. Original inspected tables below are unchanged. See [scrap](SCRAP.md) for the new columns and lifecycle.

Read-only metadata inspection and logical export; application not started against the source.
MySQL 8.0.31; 46 InnoDB base tables; 45 PRIMARY KEY, 27 UNIQUE, 62 FOREIGN KEY and 9 CHECK constraints.
No triggers, routines or scheduled events were found. Metadata visibility depends on the source account privileges.
The sole Flyway history row is a successful BASELINE at version 22; it does not prove V1–V36 execution.
The database/schema name, account, server address, defaults, records and private export are omitted.

The companion test resource `src/test/resources/db/mysql/schema-synthetic.sql` contains only reviewed CREATE TABLE statements.
It preserves table/column types, nullability, indexes, collation, FK actions and CHECK definitions; it contains zero records.
This is a point-in-time structural fixture, not a production migration or replacement for JPA.

## `app_markers`

| Column | MySQL type | Nullable |
|---|---|---|
| `applied_at` | `datetime(6)` | NO |
| `name` | `varchar(64)` | NO |

- PRIMARY KEY: `PRIMARY` (name).

## `flyway_schema_history`

| Column | MySQL type | Nullable |
|---|---|---|
| `checksum` | `int` | YES |
| `description` | `varchar(200)` | NO |
| `execution_time` | `int` | NO |
| `installed_by` | `varchar(100)` | NO |
| `installed_on` | `timestamp` | NO |
| `installed_rank` | `int` | NO |
| `script` | `varchar(1000)` | NO |
| `success` | `tinyint(1)` | NO |
| `type` | `varchar(20)` | NO |
| `version` | `varchar(50)` | YES |

- PRIMARY KEY: `PRIMARY` (installed_rank).

## `heat_treatment_batches`

| Column | MySQL type | Nullable |
|---|---|---|
| `batch_code` | `varchar(50)` | NO |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `duration_hours` | `decimal(6,2)` | YES |
| `furnace_size` | `enum('ONE_METER','TWO_METER','TWO_METER_C')` | YES |
| `id` | `binary(16)` | NO |
| `notes` | `text` | YES |
| `temperature_c` | `decimal(7,2)` | YES |
| `treatment_date` | `date` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_heat_treatment_batch_code` (batch_code).

## `melt_checks`

| Column | MySQL type | Nullable |
|---|---|---|
| `id` | `binary(16)` | NO |
| `kind` | `varchar(32)` | NO |
| `notes` | `varchar(2000)` | YES |
| `passport_id` | `binary(16)` | NO |
| `status` | `varchar(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_melt_check_kind` (kind, passport_id).
- FOREIGN KEY: `FKoewp54w6dw9rrbp35ndbeew9a` (passport_id).

## `melt_consumptions`

| Column | MySQL type | Nullable |
|---|---|---|
| `added_at` | `time` | YES |
| `base_quantity` | `decimal(38,12)` | YES |
| `base_unit` | `varchar(16)` | NO |
| `batch_number` | `varchar(160)` | YES |
| `category` | `varchar(100)` | NO |
| `id` | `binary(16)` | NO |
| `material_id` | `binary(16)` | NO |
| `material_name` | `varchar(160)` | NO |
| `notes` | `varchar(2000)` | YES |
| `passport_id` | `binary(16)` | NO |
| `position` | `int` | NO |
| `quantity` | `decimal(24,8)` | YES |
| `unit` | `varchar(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FK86r0npovu4a94xojca97poeep` (material_id).
- FOREIGN KEY: `FKjyog1tm7c4gds31xhu7m9rxvl` (passport_id).

## `melt_materials`

| Column | MySQL type | Nullable |
|---|---|---|
| `active` | `bit(1)` | NO |
| `category` | `varchar(100)` | NO |
| `id` | `binary(16)` | NO |
| `name` | `varchar(160)` | NO |
| `normalized_name` | `varchar(160)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UK2rrm9fhgjwoxkd20tvme0wq7e` (normalized_name).

## `melt_passport_events`

| Column | MySQL type | Nullable |
|---|---|---|
| `action` | `varchar(32)` | NO |
| `actor_id` | `binary(16)` | YES |
| `actor_name` | `varchar(255)` | NO |
| `id` | `binary(16)` | NO |
| `new_values` | `mediumtext` | YES |
| `occurred_at` | `datetime(6)` | NO |
| `old_values` | `mediumtext` | YES |
| `passport_id` | `binary(16)` | NO |
| `reason` | `varchar(2000)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKs10w8us1a8yfon14d5vriy2v9` (passport_id).

## `melt_passport_pieces`

| Column | MySQL type | Nullable |
|---|---|---|
| `active_piece_key` | `binary(16)` | YES |
| `id` | `binary(16)` | NO |
| `passport_id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `piece_name` | `varchar(255)` | NO |
| `piece_ref` | `varchar(255)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_melt_passport_piece` (passport_id, piece_id).
- UNIQUE: `UKl0f4ml1o9jfhub5jprahw0dmc` (active_piece_key).
- FOREIGN KEY: `FKour8u2bj4qk9wbbl11f7f7g95` (passport_id).
- FOREIGN KEY: `FKvwa334t6jbisukpv4wgi1196` (piece_id).

## `melt_passports`

| Column | MySQL type | Nullable |
|---|---|---|
| `additional_notes` | `varchar(4000)` | YES |
| `approved_at` | `datetime(6)` | YES |
| `approved_by` | `varchar(255)` | YES |
| `approved_by_id` | `binary(16)` | YES |
| `argon_humidity_percent` | `decimal(24,8)` | YES |
| `argon_injection_duration` | `decimal(24,8)` | YES |
| `assigned_operator_id` | `binary(16)` | YES |
| `assigned_operator_name` | `varchar(255)` | YES |
| `correction_reason` | `varchar(2000)` | YES |
| `created_at` | `datetime(6)` | YES |
| `created_by` | `varchar(255)` | YES |
| `created_by_id` | `binary(16)` | YES |
| `flux_injection_duration` | `decimal(24,8)` | YES |
| `furnace_number` | `int` | YES |
| `id` | `binary(16)` | NO |
| `ingot_type` | `varchar(160)` | YES |
| `injected_coverall_type` | `varchar(255)` | YES |
| `injected_coverall_weight` | `decimal(24,8)` | YES |
| `injection_operator` | `varchar(160)` | YES |
| `injection_start_time` | `time` | YES |
| `lance_number` | `varchar(255)` | YES |
| `lance_outlet_diameter` | `decimal(24,8)` | YES |
| `magnesium_calculated_at` | `datetime(6)` | YES |
| `magnesium_result` | `decimal(38,12)` | YES |
| `melt_date` | `date` | YES |
| `mg` | `decimal(24,8)` | YES |
| `mg610` | `decimal(24,8)` | YES |
| `mg_ar` | `decimal(24,8)` | YES |
| `mg_rotary` | `decimal(24,8)` | YES |
| `mgb` | `decimal(24,8)` | YES |
| `mgw` | `decimal(24,8)` | YES |
| `notes` | `varchar(4000)` | YES |
| `nozzle_full` | `bit(1)` | YES |
| `nozzle_lock` | `varchar(16)` | YES |
| `number` | `varchar(48)` | NO |
| `pouring_temperature` | `decimal(24,8)` | YES |
| `pressure_holding_time` | `decimal(24,8)` | YES |
| `primary_batch` | `varchar(160)` | YES |
| `primary_weight` | `decimal(24,8)` | YES |
| `recycled_batch` | `varchar(160)` | YES |
| `recycled_weight` | `decimal(24,8)` | YES |
| `returned_at` | `datetime(6)` | YES |
| `returned_by` | `varchar(255)` | YES |
| `returned_by_id` | `binary(16)` | YES |
| `returned_melt_weight` | `decimal(24,8)` | YES |
| `rotary_duration` | `decimal(24,8)` | YES |
| `rotary_flow` | `decimal(24,8)` | YES |
| `rotary_speed` | `decimal(24,8)` | YES |
| `rotary_temperature` | `decimal(24,8)` | YES |
| `rpt_sample_status` | `varchar(255)` | YES |
| `runner_pipe_status` | `varchar(255)` | YES |
| `spiral_test` | `decimal(24,8)` | YES |
| `status` | `varchar(32)` | NO |
| `submitted_at` | `datetime(6)` | YES |
| `submitted_by` | `varchar(255)` | YES |
| `submitted_by_id` | `binary(16)` | YES |
| `superheat_temperature` | `decimal(24,8)` | YES |
| `time_at610` | `decimal(24,8)` | YES |
| `time_at610_clock` | `time` | YES |
| `updated_at` | `datetime(6)` | YES |
| `updated_by` | `varchar(255)` | YES |
| `updated_by_id` | `binary(16)` | YES |
| `version` | `bigint` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UKgfy7cw3fy56hsno6o0xj52i7i` (number).

## `performance_criteria`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `deleted_at` | `datetime(6)` | YES |
| `description` | `varchar(2000)` | YES |
| `id` | `binary(16)` | NO |
| `maximum_score` | `decimal(12,2)` | NO |
| `period_id` | `binary(16)` | NO |
| `score` | `decimal(12,2)` | NO |
| `title` | `varchar(200)` | NO |
| `weight` | `decimal(12,2)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKvhu6oado2dmaf39ufoqdc637` (period_id).

## `performance_events`

| Column | MySQL type | Nullable |
|---|---|---|
| `action` | `varchar(40)` | NO |
| `actor_id` | `binary(16)` | NO |
| `actor_name` | `varchar(255)` | YES |
| `actor_username` | `varchar(255)` | NO |
| `criterion_id` | `binary(16)` | YES |
| `id` | `binary(16)` | NO |
| `new_efficiency` | `decimal(5,4)` | YES |
| `new_values` | `mediumtext` | YES |
| `occurred_at` | `datetime(6)` | NO |
| `old_efficiency` | `decimal(5,4)` | YES |
| `old_values` | `mediumtext` | YES |
| `period_id` | `binary(16)` | NO |
| `reason` | `varchar(2000)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKbo2gqs024bh8lt6ccdbtyaq9w` (period_id).

## `performance_periods`

| Column | MySQL type | Nullable |
|---|---|---|
| `closed_at` | `datetime(6)` | YES |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `open_user_key` | `varchar(36)` | YES |
| `revision` | `bigint` | NO |
| `title` | `varchar(160)` | NO |
| `updated_at` | `datetime(6)` | NO |
| `user_id` | `binary(16)` | NO |
| `version` | `bigint` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_performance_open_user` (open_user_key).
- FOREIGN KEY: `FKtppilb9gftr5lp13ugwxeip59` (user_id).

## `piece_assignment_history`

| Column | MySQL type | Nullable |
|---|---|---|
| `assignment_description` | `varchar(20)` | YES |
| `changed_at` | `datetime(6)` | NO |
| `changed_by` | `varchar(255)` | YES |
| `el` | `varchar(50)` | YES |
| `hb` | `varchar(50)` | YES |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `row_index` | `int` | NO |
| `test_plate_name` | `varchar(100)` | YES |
| `uts` | `varchar(50)` | YES |
| `ys` | `varchar(50)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKj35awo4ids0x4iyk6thjg9fhw` (piece_id).

## `piece_definitions`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `drawing_image_path` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `min_el` | `decimal(10,2)` | YES |
| `min_hb` | `decimal(10,2)` | YES |
| `min_uts` | `decimal(10,2)` | YES |
| `min_ys` | `decimal(10,2)` | YES |
| `piece_name` | `varchar(255)` | NO |
| `project_id` | `binary(16)` | NO |
| `row_num` | `int` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_project_row_num` (row_num, project_id).
- FOREIGN KEY: `FKf57w0h6el32rskmlolq83tdjk` (project_id).

## `piece_heat_treatments`

| Column | MySQL type | Nullable |
|---|---|---|
| `batch_id` | `binary(16)` | NO |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `details` | `text` | YES |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `tensile_disposition_id` | `binary(16)` | YES |
| `test_plate_hardness_hb` | `decimal(10,2)` | YES |
| `test_plate_label` | `varchar(20)` | YES |
| `treatment_stage` | `varchar(50)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_piece_heat_treatment` (batch_id, piece_id).
- FOREIGN KEY: `FK3f6iq3ucgr73wauutrgx0emfg` (batch_id).
- FOREIGN KEY: `FKeq8nqdf8vxqlju2y6htu6212y` (tensile_disposition_id).
- FOREIGN KEY: `FKrkmccvfw004ynou1ub1orumn3` (piece_id).

## `piece_notes`

| Column | MySQL type | Nullable |
|---|---|---|
| `content` | `text` | NO |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `created_by_username` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKcqrh0x4fusi47r5prjih89tio` (piece_id).

## `piece_stage_history`

| Column | MySQL type | Nullable |
|---|---|---|
| `changed_at` | `datetime(6)` | NO |
| `changed_by` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `stage` | `enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING')` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKampal8tpmlqfh2428287i4xs0` (piece_id).

## `piece_stage_process_dates`

| Column | MySQL type | Nullable |
|---|---|---|
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `process_date` | `date` | NO |
| `stage` | `enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING')` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_piece_stage_process_date` (stage, piece_id).
- FOREIGN KEY: `FKh72bsskn58pg0ddtl8m9atq5u` (piece_id).

## `piece_sub_stage_history`

| Column | MySQL type | Nullable |
|---|---|---|
| `changed_at` | `datetime(6)` | NO |
| `changed_by` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `kind` | `varchar(32)` | NO |
| `piece_id` | `binary(16)` | NO |
| `sub_stage` | `varchar(64)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKfys01fyxli2sbw0jyab6r2gke` (piece_id).

## `piece_sub_stage_process_dates`

| Column | MySQL type | Nullable |
|---|---|---|
| `id` | `binary(16)` | NO |
| `kind` | `varchar(32)` | NO |
| `piece_id` | `binary(16)` | NO |
| `process_date` | `date` | NO |
| `sub_stage` | `varchar(64)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_piece_sub_stage_process_date` (kind, sub_stage, piece_id).
- FOREIGN KEY: `FK6twvwb916wjpa0lw0175xlmg1` (piece_id).

## `piece_welding_dispatch_defects`

| Column | MySQL type | Nullable |
|---|---|---|
| `defect_id` | `binary(16)` | NO |
| `dispatch_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (dispatch_id, defect_id).
- FOREIGN KEY: `FKnf6sob32ig1p8civlmhhjcfyn` (defect_id).
- FOREIGN KEY: `FKpxso3hdeu3hgjetyejy5ech3t` (dispatch_id).

## `piece_welding_dispatches`

| Column | MySQL type | Nullable |
|---|---|---|
| `completed_at` | `datetime(6)` | YES |
| `completed_by` | `varchar(255)` | YES |
| `defect_type` | `varchar(255)` | NO |
| `defect_type_custom` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `notes` | `text` | YES |
| `origin_heat_treatment_sub_stage` | `enum('AWAITING_OPERATIONS','CNC_SAMPLE','STAGE_1','STAGE_2','STAGE_3','STAGE_4','STAGE_5')` | YES |
| `origin_radiography_sub_stage` | `varchar(32)` | YES |
| `origin_rough_machining_sub_stage` | `enum('DEBURRING','INITIAL_CORRECTION','MACHINING')` | YES |
| `origin_stage` | `enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING')` | YES |
| `piece_id` | `binary(16)` | NO |
| `sent_at` | `datetime(6)` | NO |
| `sent_by` | `varchar(255)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKt6cd4es3oqwm0xe6a0vo4p0gb` (piece_id).
- CHECK: `piece_welding_dispatches_chk_1`.

## `pieces`

| Column | MySQL type | Nullable |
|---|---|---|
| `archived_at` | `datetime(6)` | YES |
| `archived_by` | `varchar(255)` | YES |
| `assign1_description` | `varchar(20)` | YES |
| `assign1_el` | `varchar(50)` | YES |
| `assign1_hb` | `varchar(50)` | YES |
| `assign1_test_plate_name` | `varchar(100)` | YES |
| `assign1_uts` | `varchar(50)` | YES |
| `assign1_ys` | `varchar(50)` | YES |
| `assign2_description` | `varchar(20)` | YES |
| `assign2_el` | `varchar(50)` | YES |
| `assign2_hb` | `varchar(50)` | YES |
| `assign2_test_plate_name` | `varchar(100)` | YES |
| `assign2_uts` | `varchar(50)` | YES |
| `assign2_ys` | `varchar(50)` | YES |
| `created_at` | `datetime(6)` | NO |
| `current_stage` | `enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING')` | NO |
| `description` | `text` | YES |
| `heat_treatment_sub_stage` | `enum('AWAITING_OPERATIONS','CNC_SAMPLE','STAGE_1','STAGE_2','STAGE_3','STAGE_4','STAGE_5')` | YES |
| `id` | `binary(16)` | NO |
| `piece_definition_id` | `binary(16)` | NO |
| `piece_number` | `int` | NO |
| `process_date` | `date` | YES |
| `radiography_sub_stage` | `varchar(32)` | YES |
| `rough_machining_sub_stage` | `enum('DEBURRING','INITIAL_CORRECTION','MACHINING')` | YES |
| `version` | `bigint` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_project_row_piece` (piece_number, piece_definition_id).
- FOREIGN KEY: `FKdqha0ttoyt53v15pwkhm5pxx4` (piece_definition_id).

## `projects`

| Column | MySQL type | Nullable |
|---|---|---|
| `code` | `varchar(255)` | NO |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `name` | `varchar(255)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UKclujw4wu21d33ssgde022aymk` (code).

## `radiography_defect_positions`

| Column | MySQL type | Nullable |
|---|---|---|
| `defect_id` | `binary(16)` | NO |
| `end_cm` | `varchar(255)` | NO |
| `position_order` | `int` | NO |
| `start_cm` | `varchar(255)` | NO |

- PRIMARY KEY: `PRIMARY` (defect_id, position_order).
- FOREIGN KEY: `FKocd7034hdcgga0qovoilyi7v1` (defect_id).
- CHECK: `radiography_defect_positions_chk_1`.

## `radiography_defects`

| Column | MySQL type | Nullable |
|---|---|---|
| `corrective_action` | `varchar(32)` | YES |
| `course` | `varchar(255)` | NO |
| `created_at` | `datetime(6)` | NO |
| `defect_type` | `varchar(255)` | NO |
| `defect_type_custom` | `varchar(255)` | YES |
| `description` | `varchar(1000)` | YES |
| `end_cm` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `location_cm` | `varchar(255)` | YES |
| `parent_defect_id` | `binary(16)` | YES |
| `piece_id` | `binary(16)` | NO |
| `recorded_by` | `varchar(255)` | YES |
| `report_line_id` | `binary(16)` | YES |
| `reshoot_ok` | `bit(1)` | YES |
| `start_cm` | `varchar(255)` | YES |
| `status` | `varchar(32)` | NO |
| `type_code` | `varchar(255)` | NO |
| `welded` | `bit(1)` | NO |
| `welding_confirmed` | `bit(1)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UKpcl5qvbmxsy0m1u7d7mea0wso` (parent_defect_id).
- FOREIGN KEY: `FK5un7uqhx02e6epke2kup2d8be` (report_line_id).
- FOREIGN KEY: `FK93thc3568rmqehlt93fau7pa6` (piece_id).

## `radiography_report_lines`

| Column | MySQL type | Nullable |
|---|---|---|
| `id` | `binary(16)` | NO |
| `iqi_real` | `varchar(50)` | YES |
| `report_id` | `binary(16)` | NO |
| `result` | `varchar(20)` | YES |
| `template_line_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_report_line` (report_id, template_line_id).
- FOREIGN KEY: `FK2q9944lmcj9nntk6stpjbjkdc` (template_line_id).
- FOREIGN KEY: `FKi7w46eainwngrnbfo9d0xbqg4` (report_id).

## `radiography_report_template_lines`

| Column | MySQL type | Nullable |
|---|---|---|
| `acc_criteria` | `varchar(50)` | YES |
| `course_code` | `varchar(50)` | NO |
| `id` | `binary(16)` | NO |
| `image_count` | `varchar(50)` | YES |
| `iqi_abs` | `varchar(50)` | YES |
| `number_image` | `int` | YES |
| `sort_order` | `int` | NO |
| `template_id` | `binary(16)` | NO |
| `th` | `varchar(50)` | YES |
| `thickness_mm` | `decimal(8,2)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_template_line_no` (template_id, course_code).
- FOREIGN KEY: `FKcxwpr6yk8e9j7k4ovp03apf6l` (template_id).

## `radiography_report_templates`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `name` | `varchar(150)` | NO |
| `piece_definition_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_template_name` (name, piece_definition_id).
- FOREIGN KEY: `FKa5rtjtglvsgukmjsm0u4y7jmw` (piece_definition_id).

## `radiography_report_versions`

| Column | MySQL type | Nullable |
|---|---|---|
| `content_hash` | `varchar(64)` | NO |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | NO |
| `id` | `binary(16)` | NO |
| `kind` | `varchar(32)` | NO |
| `piece_id` | `binary(16)` | NO |
| `snapshot` | `mediumtext` | NO |
| `version_no` | `int` | NO |
| `workflow_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_version_piece_no` (piece_id, version_no).
- FOREIGN KEY: `FKtd3ul8979t9b77eq8ua8ispj2` (workflow_id).
- CHECK: `radiography_report_versions_chk_1`.

## `radiography_reports`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `part_name` | `varchar(255)` | YES |
| `piece_id` | `binary(16)` | NO |
| `project_code` | `varchar(255)` | YES |
| `reference_standard` | `varchar(255)` | YES |
| `report_date` | `date` | YES |
| `report_number` | `varchar(80)` | YES |
| `request_number` | `varchar(80)` | YES |
| `status` | `varchar(32)` | NO |
| `template_id` | `binary(16)` | NO |
| `updated_at` | `datetime(6)` | YES |
| `updated_by` | `varchar(255)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_report_piece_template` (piece_id, template_id).
- FOREIGN KEY: `FK3iyhbx5wp9nfp2jhkqh47bfp2` (template_id).
- FOREIGN KEY: `FKefwj87h7dl7wpyhnht5iihvfd` (piece_id).

## `radiography_workflow_approvals`

| Column | MySQL type | Nullable |
|---|---|---|
| `approval_type` | `varchar(16)` | NO |
| `approved_at` | `datetime(6)` | NO |
| `approver_name` | `varchar(255)` | NO |
| `approver_role` | `varchar(32)` | NO |
| `approver_user_id` | `binary(16)` | NO |
| `approver_username` | `varchar(255)` | NO |
| `decision` | `varchar(16)` | NO |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `rejection_reason` | `text` | YES |
| `report_version_id` | `binary(16)` | YES |
| `round` | `int` | NO |
| `signature_content_type` | `varchar(64)` | YES |
| `signature_path` | `varchar(255)` | YES |
| `workflow_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FK9t563sc304iceujgshrouttiw` (workflow_id).
- CHECK: `radiography_workflow_approvals_chk_1`.

## `radiography_workflow_events`

| Column | MySQL type | Nullable |
|---|---|---|
| `action` | `varchar(48)` | NO |
| `actor_name` | `varchar(255)` | YES |
| `actor_user_id` | `binary(16)` | YES |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `new_value` | `varchar(255)` | YES |
| `old_value` | `varchar(255)` | YES |
| `piece_id` | `binary(16)` | NO |
| `reason` | `text` | YES |
| `workflow_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKh5we79wmcbn8n3txgtox78a6p` (workflow_id).
- CHECK: `radiography_workflow_events_chk_1`.

## `radiography_workflows`

| Column | MySQL type | Nullable |
|---|---|---|
| `assigned_at` | `datetime(6)` | NO |
| `assigned_by_id` | `binary(16)` | NO |
| `completed_at` | `datetime(6)` | YES |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `interpreter_id` | `binary(16)` | NO |
| `interpreter_task_id` | `binary(16)` | YES |
| `manager_task_id` | `binary(16)` | YES |
| `open_key` | `varchar(36)` | YES |
| `piece_id` | `binary(16)` | NO |
| `rejected_at` | `datetime(6)` | YES |
| `rejected_by` | `varchar(255)` | YES |
| `rejection_reason` | `text` | YES |
| `round` | `int` | NO |
| `status` | `varchar(32)` | NO |
| `updated_at` | `datetime(6)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_rg_workflow_open_piece` (open_key).
- FOREIGN KEY: `FK4qcy090nurc6o6dj26s5ir5aq` (manager_task_id).
- FOREIGN KEY: `FK6428l9557233apd2kuy8iakn5` (interpreter_id).
- FOREIGN KEY: `FK8yhfp0ffb9b96nukqw7x4gddv` (piece_id).
- FOREIGN KEY: `FKgoc5lyeg5eammadr5gtsxljid` (assigned_by_id).
- FOREIGN KEY: `FKp6h5xb15ajlmh1njbi7ucrc5q` (interpreter_task_id).
- CHECK: `radiography_workflows_chk_1`.

## `system_notification_reads`

| Column | MySQL type | Nullable |
|---|---|---|
| `dismissed` | `bit(1)` | NO |
| `id` | `binary(16)` | NO |
| `notification_id` | `binary(16)` | NO |
| `read_at` | `datetime(6)` | NO |
| `user_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_system_notification_read_user` (notification_id, user_id).
- FOREIGN KEY: `FKefgm0phk08v68cofws1dy8eyh` (notification_id).
- FOREIGN KEY: `FKrnkdotb7wnwj57kfgjw1w2xjb` (user_id).

## `system_notifications`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `dedupe_key` | `varchar(128)` | YES |
| `id` | `binary(16)` | NO |
| `message` | `varchar(512)` | NO |
| `piece_id` | `binary(16)` | YES |
| `piece_name` | `varchar(255)` | YES |
| `piece_number` | `int` | YES |
| `project_code` | `varchar(255)` | YES |
| `recipient_id` | `binary(16)` | YES |
| `task_id` | `binary(16)` | YES |
| `tensile_test_id` | `binary(16)` | YES |
| `type` | `varchar(64)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UK93cc6rwy59ghcb89md3ukwynh` (tensile_test_id).
- FOREIGN KEY: `FK4etgmt6ovsv2f3kyx5bb56hbd` (piece_id).
- FOREIGN KEY: `FK8qc5qem65dg2gx2unshilcrvb` (task_id).
- FOREIGN KEY: `FKe1mh3povy24gokmc0pl0ss2nf` (tensile_test_id).
- FOREIGN KEY: `FKqnoo8w7v7t9l538j7h06bjtp0` (recipient_id).

## `task_activities`

| Column | MySQL type | Nullable |
|---|---|---|
| `action` | `varchar(48)` | NO |
| `actor_id` | `binary(16)` | NO |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `new_value` | `varchar(500)` | YES |
| `note` | `varchar(500)` | YES |
| `old_value` | `varchar(500)` | YES |
| `task_id` | `binary(16)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKkgc6x6jdghxwivve0euo67sev` (task_id).
- FOREIGN KEY: `FKorrw5sneuadasoivv5e4f1chy` (actor_id).
- CHECK: `task_activities_chk_1`.

## `task_checklist_items`

| Column | MySQL type | Nullable |
|---|---|---|
| `completed` | `bit(1)` | NO |
| `completed_at` | `datetime(6)` | YES |
| `completed_by` | `binary(16)` | YES |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `sort_order` | `int` | NO |
| `task_id` | `binary(16)` | NO |
| `title` | `varchar(255)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FK4noi2hr7x7iv1o8cra1osagua` (completed_by).
- FOREIGN KEY: `FKdss8rgar5gq2b5vjnjbapn4cu` (task_id).

## `task_comments`

| Column | MySQL type | Nullable |
|---|---|---|
| `author_id` | `binary(16)` | NO |
| `content` | `text` | NO |
| `created_at` | `datetime(6)` | NO |
| `id` | `binary(16)` | NO |
| `task_id` | `binary(16)` | NO |
| `updated_at` | `datetime(6)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FK9517viwn2geh1gpivj6l9y64u` (task_id).
- FOREIGN KEY: `FKtbd7uwo21s4shik5f0mghfk21` (author_id).

## `tasks`

| Column | MySQL type | Nullable |
|---|---|---|
| `assigned_to` | `binary(16)` | NO |
| `completed_at` | `datetime(6)` | YES |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `binary(16)` | NO |
| `description` | `text` | YES |
| `due_date` | `datetime(6)` | YES |
| `id` | `binary(16)` | NO |
| `part_id` | `binary(16)` | YES |
| `priority` | `varchar(32)` | NO |
| `project_id` | `binary(16)` | YES |
| `status` | `varchar(32)` | NO |
| `task_type` | `varchar(48)` | YES |
| `title` | `varchar(200)` | NO |
| `updated_at` | `datetime(6)` | NO |
| `workflow_id` | `binary(16)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FK2vjo8mbre3rvpbd6e7976b54m` (assigned_to).
- FOREIGN KEY: `FK9dgm9t7wn4w3gh57h63g712lo` (created_by).
- FOREIGN KEY: `FKcfaq3oc7k72suc1qdokksik2w` (part_id).
- FOREIGN KEY: `FKsfhn82y57i3k9uxww1s007acc` (project_id).
- CHECK: `tasks_chk_1`.
- CHECK: `tasks_chk_2`.

## `tensile_test_dispositions`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `disposition_date` | `date` | NO |
| `duration_hours` | `decimal(6,2)` | YES |
| `id` | `binary(16)` | NO |
| `inspection_result` | `enum('ACCEPTED','REJECTED')` | NO |
| `next_stage` | `enum('AGING','APPROVED','CONCESSION_APPROVAL','NEXT_TENSILE','OTHER','RE_OPERATION','SCRAP')` | NO |
| `notes` | `text` | YES |
| `quantity` | `int` | YES |
| `responsible_user_id` | `binary(16)` | YES |
| `system_generated` | `bit(1)` | NO |
| `temperature_c` | `decimal(7,2)` | YES |
| `tensile_test_id` | `binary(16)` | NO |
| `updated_at` | `datetime(6)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_tensile_disposition_test` (tensile_test_id).
- FOREIGN KEY: `FK19gr234t9ng9ekjlj917ukc36` (tensile_test_id).
- FOREIGN KEY: `FK3xdhw3kkhpehn4s11n3mygxk3` (responsible_user_id).

## `tensile_test_samples`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `description` | `text` | YES |
| `el` | `decimal(10,2)` | YES |
| `hb` | `decimal(10,2)` | YES |
| `id` | `binary(16)` | NO |
| `sample_index` | `int` | NO |
| `sample_label` | `varchar(20)` | YES |
| `tensile_test_id` | `binary(16)` | NO |
| `uts` | `decimal(10,2)` | YES |
| `ys` | `decimal(10,2)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_tensile_sample_index` (sample_index, tensile_test_id).
- FOREIGN KEY: `FKayh504cx5t8t60o221eawdsm0` (tensile_test_id).

## `tensile_tests`

| Column | MySQL type | Nullable |
|---|---|---|
| `below_threshold` | `bit(1)` | NO |
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `piece_id` | `binary(16)` | NO |
| `reviewed_at` | `datetime(6)` | YES |
| `reviewed_by` | `varchar(255)` | YES |
| `status` | `enum('APPROVED','PENDING_REVIEW','REJECTED','RETEST_REQUIRED')` | NO |
| `test_index` | `int` | NO |
| `threshold_failures` | `varchar(64)` | YES |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `uk_tensile_piece_index` (test_index, piece_id).
- FOREIGN KEY: `FKnd3e58hysh8camrjo67v6t3ur` (piece_id).

## `user_permissions`

| Column | MySQL type | Nullable |
|---|---|---|
| `permission` | `varchar(64)` | NO |
| `user_id` | `binary(16)` | NO |

- UNIQUE: `UKo090f18qtl3dv36wdih9fm3k8` (user_id, permission).
- FOREIGN KEY: `FKkowxl8b2bngrxd1gafh13005u` (user_id).

## `users`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `department` | `varchar(100)` | YES |
| `disabled` | `bit(1)` | NO |
| `full_name` | `varchar(255)` | YES |
| `id` | `binary(16)` | NO |
| `last_activity_at` | `datetime(6)` | YES |
| `password` | `varchar(255)` | NO |
| `role` | `varchar(32)` | NO |
| `signature_content_type` | `varchar(64)` | YES |
| `signature_path` | `varchar(255)` | YES |
| `signature_updated_at` | `datetime(6)` | YES |
| `username` | `varchar(255)` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- UNIQUE: `UKr43af9ap4edm43mmtq01oddj6` (username).

## `welding_map_marks`

| Column | MySQL type | Nullable |
|---|---|---|
| `created_at` | `datetime(6)` | NO |
| `created_by` | `varchar(255)` | YES |
| `defect_type` | `varchar(255)` | NO |
| `id` | `binary(16)` | NO |
| `note` | `text` | YES |
| `piece_id` | `binary(16)` | NO |
| `x_percent` | `double` | NO |
| `y_percent` | `double` | NO |

- PRIMARY KEY: `PRIMARY` (id).
- FOREIGN KEY: `FKebt27fq7jj3k9okr6w83xffq5` (piece_id).

## Foreign keys

| Source | Target | Update | Delete |
|---|---|---|---|
| `melt_checks.passport_id` | `melt_passports.id` | NO ACTION | NO ACTION |
| `melt_consumptions.material_id` | `melt_materials.id` | NO ACTION | NO ACTION |
| `melt_consumptions.passport_id` | `melt_passports.id` | NO ACTION | NO ACTION |
| `melt_passport_events.passport_id` | `melt_passports.id` | NO ACTION | NO ACTION |
| `melt_passport_pieces.passport_id` | `melt_passports.id` | NO ACTION | NO ACTION |
| `melt_passport_pieces.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `performance_criteria.period_id` | `performance_periods.id` | NO ACTION | NO ACTION |
| `performance_events.period_id` | `performance_periods.id` | NO ACTION | NO ACTION |
| `performance_periods.user_id` | `users.id` | NO ACTION | NO ACTION |
| `piece_assignment_history.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_definitions.project_id` | `projects.id` | NO ACTION | NO ACTION |
| `piece_heat_treatments.batch_id` | `heat_treatment_batches.id` | NO ACTION | NO ACTION |
| `piece_heat_treatments.tensile_disposition_id` | `tensile_test_dispositions.id` | NO ACTION | NO ACTION |
| `piece_heat_treatments.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_notes.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_stage_history.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_stage_process_dates.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_sub_stage_history.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_sub_stage_process_dates.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `piece_welding_dispatch_defects.defect_id` | `radiography_defects.id` | NO ACTION | NO ACTION |
| `piece_welding_dispatch_defects.dispatch_id` | `piece_welding_dispatches.id` | NO ACTION | NO ACTION |
| `piece_welding_dispatches.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `pieces.piece_definition_id` | `piece_definitions.id` | NO ACTION | NO ACTION |
| `radiography_defect_positions.defect_id` | `radiography_defects.id` | NO ACTION | NO ACTION |
| `radiography_defects.report_line_id` | `radiography_report_lines.id` | NO ACTION | NO ACTION |
| `radiography_defects.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `radiography_report_lines.template_line_id` | `radiography_report_template_lines.id` | NO ACTION | NO ACTION |
| `radiography_report_lines.report_id` | `radiography_reports.id` | NO ACTION | NO ACTION |
| `radiography_report_template_lines.template_id` | `radiography_report_templates.id` | NO ACTION | NO ACTION |
| `radiography_report_templates.piece_definition_id` | `piece_definitions.id` | NO ACTION | NO ACTION |
| `radiography_report_versions.workflow_id` | `radiography_workflows.id` | NO ACTION | CASCADE |
| `radiography_reports.template_id` | `radiography_report_templates.id` | NO ACTION | NO ACTION |
| `radiography_reports.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `radiography_workflow_approvals.workflow_id` | `radiography_workflows.id` | NO ACTION | CASCADE |
| `radiography_workflow_events.workflow_id` | `radiography_workflows.id` | NO ACTION | CASCADE |
| `radiography_workflows.manager_task_id` | `tasks.id` | NO ACTION | SET NULL |
| `radiography_workflows.interpreter_id` | `users.id` | NO ACTION | NO ACTION |
| `radiography_workflows.piece_id` | `pieces.id` | NO ACTION | CASCADE |
| `radiography_workflows.assigned_by_id` | `users.id` | NO ACTION | NO ACTION |
| `radiography_workflows.interpreter_task_id` | `tasks.id` | NO ACTION | SET NULL |
| `system_notification_reads.notification_id` | `system_notifications.id` | NO ACTION | NO ACTION |
| `system_notification_reads.user_id` | `users.id` | NO ACTION | NO ACTION |
| `system_notifications.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `system_notifications.task_id` | `tasks.id` | NO ACTION | CASCADE |
| `system_notifications.tensile_test_id` | `tensile_tests.id` | NO ACTION | NO ACTION |
| `system_notifications.recipient_id` | `users.id` | NO ACTION | CASCADE |
| `task_activities.task_id` | `tasks.id` | NO ACTION | CASCADE |
| `task_activities.actor_id` | `users.id` | NO ACTION | NO ACTION |
| `task_checklist_items.completed_by` | `users.id` | NO ACTION | SET NULL |
| `task_checklist_items.task_id` | `tasks.id` | NO ACTION | CASCADE |
| `task_comments.task_id` | `tasks.id` | NO ACTION | CASCADE |
| `task_comments.author_id` | `users.id` | NO ACTION | NO ACTION |
| `tasks.assigned_to` | `users.id` | NO ACTION | NO ACTION |
| `tasks.created_by` | `users.id` | NO ACTION | NO ACTION |
| `tasks.part_id` | `pieces.id` | NO ACTION | SET NULL |
| `tasks.project_id` | `projects.id` | NO ACTION | SET NULL |
| `tensile_test_dispositions.tensile_test_id` | `tensile_tests.id` | NO ACTION | NO ACTION |
| `tensile_test_dispositions.responsible_user_id` | `users.id` | NO ACTION | NO ACTION |
| `tensile_test_samples.tensile_test_id` | `tensile_tests.id` | NO ACTION | NO ACTION |
| `tensile_tests.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
| `user_permissions.user_id` | `users.id` | NO ACTION | NO ACTION |
| `welding_map_marks.piece_id` | `pieces.id` | NO ACTION | NO ACTION |
