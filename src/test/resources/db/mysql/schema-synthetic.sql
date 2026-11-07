-- Structural fixture: 2026-10-30, MySQL 8.0.31. No records or private connection metadata.
-- Load only into an empty IsolatedMysql-owned synthetic schema.

CREATE TABLE `app_markers` (
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applied_at` datetime(6) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `heat_treatment_batches` (
  `version` BIGINT NOT NULL DEFAULT 0,
  `id` binary(16) NOT NULL,
  `batch_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration_hours` decimal(6,2) DEFAULT NULL,
  `furnace_size` enum('ONE_METER','TWO_METER','TWO_METER_C') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `temperature_c` decimal(7,2) DEFAULT NULL,
  `treatment_date` date NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_heat_treatment_batch_code` (`batch_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_checks` (
  `id` binary(16) NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `passport_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_melt_check_kind` (`passport_id`,`kind`),
  KEY `idx_melt_check_passport` (`passport_id`),
  CONSTRAINT `FKoewp54w6dw9rrbp35ndbeew9a` FOREIGN KEY (`passport_id`) REFERENCES `melt_passports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_consumptions` (
  `id` binary(16) NOT NULL,
  `added_at` time DEFAULT NULL,
  `base_quantity` decimal(38,12) DEFAULT NULL,
  `base_unit` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `batch_number` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `material_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` int NOT NULL,
  `quantity` decimal(24,8) DEFAULT NULL,
  `unit` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `material_id` binary(16) NOT NULL,
  `passport_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_melt_consumption_material` (`material_id`),
  KEY `idx_melt_consumption_passport` (`passport_id`),
  CONSTRAINT `FK86r0npovu4a94xojca97poeep` FOREIGN KEY (`material_id`) REFERENCES `melt_materials` (`id`),
  CONSTRAINT `FKjyog1tm7c4gds31xhu7m9rxvl` FOREIGN KEY (`passport_id`) REFERENCES `melt_passports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_materials` (
  `id` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2rrm9fhgjwoxkd20tvme0wq7e` (`normalized_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_passport_events` (
  `id` binary(16) NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` binary(16) DEFAULT NULL,
  `actor_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `reason` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `passport_id` binary(16) NOT NULL,
  `new_values` mediumtext COLLATE utf8mb4_unicode_ci,
  `old_values` mediumtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_melt_event_passport_time` (`passport_id`,`occurred_at`),
  CONSTRAINT `FKs10w8us1a8yfon14d5vriy2v9` FOREIGN KEY (`passport_id`) REFERENCES `melt_passports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_passport_pieces` (
  `id` binary(16) NOT NULL,
  `active_piece_key` binary(16) DEFAULT NULL,
  `piece_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_ref` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `passport_id` binary(16) NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_melt_passport_piece` (`passport_id`,`piece_id`),
  UNIQUE KEY `UKl0f4ml1o9jfhub5jprahw0dmc` (`active_piece_key`),
  KEY `idx_melt_link_piece` (`piece_id`),
  KEY `idx_melt_link_passport` (`passport_id`),
  CONSTRAINT `FKour8u2bj4qk9wbbl11f7f7g95` FOREIGN KEY (`passport_id`) REFERENCES `melt_passports` (`id`),
  CONSTRAINT `FKvwa334t6jbisukpv4wgi1196` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `melt_passports` (
  `id` binary(16) NOT NULL,
  `additional_notes` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_by_id` binary(16) DEFAULT NULL,
  `correction_reason` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by_id` binary(16) DEFAULT NULL,
  `furnace_number` int DEFAULT NULL,
  `ingot_type` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `injection_operator` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `magnesium_calculated_at` datetime(6) DEFAULT NULL,
  `magnesium_result` decimal(38,12) DEFAULT NULL,
  `melt_date` date DEFAULT NULL,
  `mg` decimal(24,8) DEFAULT NULL,
  `mg610` decimal(24,8) DEFAULT NULL,
  `mg_ar` decimal(24,8) DEFAULT NULL,
  `mgb` decimal(24,8) DEFAULT NULL,
  `mg_rotary` decimal(24,8) DEFAULT NULL,
  `mgw` decimal(24,8) DEFAULT NULL,
  `notes` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_batch` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `primary_weight` decimal(24,8) DEFAULT NULL,
  `argon_humidity_percent` decimal(24,8) DEFAULT NULL,
  `argon_injection_duration` decimal(24,8) DEFAULT NULL,
  `flux_injection_duration` decimal(24,8) DEFAULT NULL,
  `injected_coverall_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `injected_coverall_weight` decimal(24,8) DEFAULT NULL,
  `injection_start_time` time DEFAULT NULL,
  `lance_number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lance_outlet_diameter` decimal(24,8) DEFAULT NULL,
  `nozzle_full` bit(1) DEFAULT NULL,
  `nozzle_lock` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pouring_temperature` decimal(24,8) DEFAULT NULL,
  `pressure_holding_time` decimal(24,8) DEFAULT NULL,
  `returned_melt_weight` decimal(24,8) DEFAULT NULL,
  `rotary_duration` decimal(24,8) DEFAULT NULL,
  `rotary_flow` decimal(24,8) DEFAULT NULL,
  `rotary_speed` decimal(24,8) DEFAULT NULL,
  `rotary_temperature` decimal(24,8) DEFAULT NULL,
  `rpt_sample_status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `runner_pipe_status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `spiral_test` decimal(24,8) DEFAULT NULL,
  `superheat_temperature` decimal(24,8) DEFAULT NULL,
  `time_at610` decimal(24,8) DEFAULT NULL,
  `recycled_batch` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recycled_weight` decimal(24,8) DEFAULT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `returned_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `returned_by_id` binary(16) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `submitted_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `submitted_by_id` binary(16) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by_id` binary(16) DEFAULT NULL,
  `version` bigint NOT NULL,
  `time_at610_clock` time DEFAULT NULL,
  `assigned_operator_id` binary(16) DEFAULT NULL,
  `assigned_operator_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgfy7cw3fy56hsno6o0xj52i7i` (`number`),
  KEY `idx_melt_status_created` (`status`,`created_at`),
  KEY `idx_melt_date_furnace` (`melt_date`,`furnace_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `performance_criteria` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `maximum_score` decimal(12,2) NOT NULL,
  `score` decimal(12,2) NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight` decimal(12,2) NOT NULL,
  `period_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_criterion_period` (`period_id`,`created_at`),
  CONSTRAINT `FKvhu6oado2dmaf39ufoqdc637` FOREIGN KEY (`period_id`) REFERENCES `performance_periods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `performance_events` (
  `id` binary(16) NOT NULL,
  `action` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` binary(16) NOT NULL,
  `actor_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `criterion_id` binary(16) DEFAULT NULL,
  `new_efficiency` decimal(5,4) DEFAULT NULL,
  `new_values` mediumtext COLLATE utf8mb4_unicode_ci,
  `occurred_at` datetime(6) NOT NULL,
  `old_efficiency` decimal(5,4) DEFAULT NULL,
  `old_values` mediumtext COLLATE utf8mb4_unicode_ci,
  `reason` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `period_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_event_period` (`period_id`,`occurred_at`),
  CONSTRAINT `FKbo2gqs024bh8lt6ccdbtyaq9w` FOREIGN KEY (`period_id`) REFERENCES `performance_periods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `performance_periods` (
  `id` binary(16) NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `open_user_key` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `revision` bigint NOT NULL,
  `title` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_performance_open_user` (`open_user_key`),
  KEY `idx_performance_user_created` (`user_id`,`created_at`),
  CONSTRAINT `FKtppilb9gftr5lp13ugwxeip59` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_assignment_history` (
  `id` binary(16) NOT NULL,
  `assignment_description` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `el` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hb` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `row_index` int NOT NULL,
  `test_plate_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uts` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ys` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKj35awo4ids0x4iyk6thjg9fhw` (`piece_id`),
  CONSTRAINT `FKj35awo4ids0x4iyk6thjg9fhw` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_definitions` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `drawing_image_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_num` int NOT NULL,
  `project_id` binary(16) NOT NULL,
  `min_el` decimal(10,2) DEFAULT NULL,
  `min_hb` decimal(10,2) DEFAULT NULL,
  `min_uts` decimal(10,2) DEFAULT NULL,
  `min_ys` decimal(10,2) DEFAULT NULL,
  `radiography_shot_hours` decimal(10,2) DEFAULT NULL,
  `radiography_interpretation_hours` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_row_num` (`project_id`,`row_num`),
  CONSTRAINT `FKf57w0h6el32rskmlolq83tdjk` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_heat_treatments` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `test_plate_hardness_hb` decimal(10,2) DEFAULT NULL,
  `test_plate_label` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `treatment_stage` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_id` binary(16) NOT NULL,
  `piece_id` binary(16) NOT NULL,
  `tensile_disposition_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_piece_heat_treatment` (`batch_id`,`piece_id`),
  KEY `FKrkmccvfw004ynou1ub1orumn3` (`piece_id`),
  KEY `FKeq8nqdf8vxqlju2y6htu6212y` (`tensile_disposition_id`),
  CONSTRAINT `FK3f6iq3ucgr73wauutrgx0emfg` FOREIGN KEY (`batch_id`) REFERENCES `heat_treatment_batches` (`id`),
  CONSTRAINT `FKeq8nqdf8vxqlju2y6htu6212y` FOREIGN KEY (`tensile_disposition_id`) REFERENCES `tensile_test_dispositions` (`id`),
  CONSTRAINT `FKrkmccvfw004ynou1ub1orumn3` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_notes` (
  `id` binary(16) NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcqrh0x4fusi47r5prjih89tio` (`piece_id`),
  CONSTRAINT `FKcqrh0x4fusi47r5prjih89tio` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_stage_history` (
  `id` binary(16) NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stage` enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKampal8tpmlqfh2428287i4xs0` (`piece_id`),
  CONSTRAINT `FKampal8tpmlqfh2428287i4xs0` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_stage_process_dates` (
  `id` binary(16) NOT NULL,
  `process_date` date NOT NULL,
  `stage` enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_piece_stage_process_date` (`piece_id`,`stage`),
  CONSTRAINT `FKh72bsskn58pg0ddtl8m9atq5u` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_sub_stage_history` (
  `id` binary(16) NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sub_stage` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfys01fyxli2sbw0jyab6r2gke` (`piece_id`),
  CONSTRAINT `FKfys01fyxli2sbw0jyab6r2gke` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_sub_stage_process_dates` (
  `id` binary(16) NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_date` date NOT NULL,
  `sub_stage` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_piece_sub_stage_process_date` (`piece_id`,`kind`,`sub_stage`),
  CONSTRAINT `FK6twvwb916wjpa0lw0175xlmg1` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_welding_dispatch_defects` (
  `dispatch_id` binary(16) NOT NULL,
  `defect_id` binary(16) NOT NULL,
  PRIMARY KEY (`dispatch_id`,`defect_id`),
  KEY `FKnf6sob32ig1p8civlmhhjcfyn` (`defect_id`),
  CONSTRAINT `FKnf6sob32ig1p8civlmhhjcfyn` FOREIGN KEY (`defect_id`) REFERENCES `radiography_defects` (`id`),
  CONSTRAINT `FKpxso3hdeu3hgjetyejy5ech3t` FOREIGN KEY (`dispatch_id`) REFERENCES `piece_welding_dispatches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `piece_welding_dispatches` (
  `origin_process_date` DATE NULL,
  `dispatch_piece_version` BIGINT NULL,
  `cancelled_at` DATETIME(6) NULL,
  `cancelled_by` VARCHAR(255) NULL,
  `cancellation_reason` VARCHAR(2000) NULL,
  `id` binary(16) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `completed_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `defect_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `defect_type_custom` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `sent_at` datetime(6) NOT NULL,
  `sent_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  `origin_heat_treatment_sub_stage` enum('AWAITING_OPERATIONS','CNC_SAMPLE','STAGE_1','STAGE_2','STAGE_3','STAGE_4','STAGE_5') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin_radiography_sub_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin_rough_machining_sub_stage` enum('DEBURRING','INITIAL_CORRECTION','MACHINING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `origin_stage` enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt6cd4es3oqwm0xe6a0vo4p0gb` (`piece_id`),
  CONSTRAINT `FKt6cd4es3oqwm0xe6a0vo4p0gb` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`),
  CONSTRAINT `piece_welding_dispatches_chk_1` CHECK ((`origin_radiography_sub_stage` in (_utf8mb4'INTERPRETATION',_utf8mb4'SHOT',_utf8mb4'VT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pieces` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_stage` enum('CASTING','DELIVERED','HEAT_TREATMENT','NOT_STARTED','RADIOGRAPHY','READY','ROUGH_MACHINING','SCRAPPED','SHOT_BLASTING','TENSILE','WELDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `heat_treatment_sub_stage` enum('AWAITING_OPERATIONS','CNC_SAMPLE','STAGE_1','STAGE_2','STAGE_3','STAGE_4','STAGE_5') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_number` int NOT NULL,
  `radiography_sub_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_definition_id` binary(16) NOT NULL,
  `assign1_description` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign1_el` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign1_hb` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign1_test_plate_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign1_uts` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign1_ys` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_description` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_el` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_hb` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_test_plate_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_uts` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assign2_ys` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `archived_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `process_date` date DEFAULT NULL,
  `rough_machining_sub_stage` enum('DEBURRING','INITIAL_CORRECTION','MACHINING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_row_piece` (`piece_definition_id`,`piece_number`),
  CONSTRAINT `FKdqha0ttoyt53v15pwkhm5pxx4` FOREIGN KEY (`piece_definition_id`) REFERENCES `piece_definitions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `projects` (
  `id` binary(16) NOT NULL,
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKclujw4wu21d33ssgde022aymk` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_defect_positions` (
  `defect_id` binary(16) NOT NULL,
  `end_cm` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_cm` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `position_order` int NOT NULL,
  PRIMARY KEY (`defect_id`,`position_order`),
  CONSTRAINT `FKocd7034hdcgga0qovoilyi7v1` FOREIGN KEY (`defect_id`) REFERENCES `radiography_defects` (`id`),
  CONSTRAINT `radiography_defect_positions_chk_1` CHECK ((`position_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_defects` (
  `id` binary(16) NOT NULL,
  `course` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `defect_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `location_cm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recorded_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `welding_confirmed` bit(1) NOT NULL,
  `piece_id` binary(16) NOT NULL,
  `defect_type_custom` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_line_id` binary(16) DEFAULT NULL,
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `end_cm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `start_cm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `corrective_action` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `welded` bit(1) NOT NULL,
  `parent_defect_id` binary(16) DEFAULT NULL,
  `reshoot_ok` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpcl5qvbmxsy0m1u7d7mea0wso` (`parent_defect_id`),
  KEY `FK93thc3568rmqehlt93fau7pa6` (`piece_id`),
  KEY `FK5un7uqhx02e6epke2kup2d8be` (`report_line_id`),
  CONSTRAINT `FK5un7uqhx02e6epke2kup2d8be` FOREIGN KEY (`report_line_id`) REFERENCES `radiography_report_lines` (`id`),
  CONSTRAINT `FK93thc3568rmqehlt93fau7pa6` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_report_lines` (
  `id` binary(16) NOT NULL,
  `iqi_real` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_id` binary(16) NOT NULL,
  `template_line_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_report_line` (`report_id`,`template_line_id`),
  KEY `FK2q9944lmcj9nntk6stpjbjkdc` (`template_line_id`),
  CONSTRAINT `FK2q9944lmcj9nntk6stpjbjkdc` FOREIGN KEY (`template_line_id`) REFERENCES `radiography_report_template_lines` (`id`),
  CONSTRAINT `FKi7w46eainwngrnbfo9d0xbqg4` FOREIGN KEY (`report_id`) REFERENCES `radiography_reports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_report_template_lines` (
  `id` binary(16) NOT NULL,
  `acc_criteria` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `iqi_abs` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number_image` int DEFAULT NULL,
  `sort_order` int NOT NULL,
  `thickness_mm` decimal(8,2) DEFAULT NULL,
  `template_id` binary(16) NOT NULL,
  `course_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_count` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `th` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_template_line_no` (`template_id`,`course_code`),
  CONSTRAINT `FKcxwpr6yk8e9j7k4ovp03apf6l` FOREIGN KEY (`template_id`) REFERENCES `radiography_report_templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_report_templates` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_definition_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_template_name` (`piece_definition_id`,`name`),
  CONSTRAINT `FKa5rtjtglvsgukmjsm0u4y7jmw` FOREIGN KEY (`piece_definition_id`) REFERENCES `piece_definitions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_report_versions` (
  `id` binary(16) NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  `snapshot` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_no` int NOT NULL,
  `workflow_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_version_piece_no` (`piece_id`,`version_no`),
  KEY `idx_rg_version_workflow` (`workflow_id`),
  CONSTRAINT `FKtd3ul8979t9b77eq8ua8ispj2` FOREIGN KEY (`workflow_id`) REFERENCES `radiography_workflows` (`id`) ON DELETE CASCADE,
  CONSTRAINT `radiography_report_versions_chk_1` CHECK ((`kind` in (_utf8mb4'ORIGINAL',_utf8mb4'INTERPRETER_APPROVED',_utf8mb4'MANAGER_FINAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_reports` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_date` date DEFAULT NULL,
  `report_number` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_number` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  `template_id` binary(16) NOT NULL,
  `part_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_standard` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_report_piece_template` (`piece_id`,`template_id`),
  KEY `FK3iyhbx5wp9nfp2jhkqh47bfp2` (`template_id`),
  CONSTRAINT `FK3iyhbx5wp9nfp2jhkqh47bfp2` FOREIGN KEY (`template_id`) REFERENCES `radiography_report_templates` (`id`),
  CONSTRAINT `FKefwj87h7dl7wpyhnht5iihvfd` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_workflow_approvals` (
  `id` binary(16) NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  `approver_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `approver_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `approver_user_id` binary(16) NOT NULL,
  `approver_username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) NOT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `report_version_id` binary(16) DEFAULT NULL,
  `round` int NOT NULL,
  `signature_content_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `signature_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workflow_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rg_approval_workflow` (`workflow_id`),
  KEY `idx_rg_approval_piece` (`piece_id`),
  CONSTRAINT `FK9t563sc304iceujgshrouttiw` FOREIGN KEY (`workflow_id`) REFERENCES `radiography_workflows` (`id`) ON DELETE CASCADE,
  CONSTRAINT `radiography_workflow_approvals_chk_1` CHECK ((`approval_type` in (_utf8mb4'INTERPRETER',_utf8mb4'MANAGER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_workflow_events` (
  `id` binary(16) NOT NULL,
  `action` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_user_id` binary(16) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `new_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `workflow_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rg_event_workflow_created` (`workflow_id`,`created_at`),
  KEY `idx_rg_event_piece` (`piece_id`),
  CONSTRAINT `FKh5we79wmcbn8n3txgtox78a6p` FOREIGN KEY (`workflow_id`) REFERENCES `radiography_workflows` (`id`) ON DELETE CASCADE,
  CONSTRAINT `radiography_workflow_events_chk_1` CHECK ((`action` in (_utf8mb4'RADIOGRAPHY_INTERPRETER_ASSIGNED',_utf8mb4'RADIOGRAPHY_INTERPRETER_REASSIGNED',_utf8mb4'RADIOGRAPHY_REPORT_STARTED',_utf8mb4'RADIOGRAPHY_REPORT_ISSUED',_utf8mb4'RADIOGRAPHY_INTERPRETER_APPROVED',_utf8mb4'RADIOGRAPHY_MANAGER_REVIEW_STARTED',_utf8mb4'RADIOGRAPHY_MANAGER_APPROVED',_utf8mb4'RADIOGRAPHY_MANAGER_REJECTED',_utf8mb4'RADIOGRAPHY_REINTERPRETATION_REQUESTED',_utf8mb4'RADIOGRAPHY_WORKFLOW_CANCELLED',_utf8mb4'PART_STAGE_CHANGED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `radiography_workflows` (
  `id` binary(16) NOT NULL,
  `assigned_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `open_key` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rejected_at` datetime(6) DEFAULT NULL,
  `rejected_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `round` int NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `assigned_by_id` binary(16) NOT NULL,
  `interpreter_id` binary(16) NOT NULL,
  `interpreter_task_id` binary(16) DEFAULT NULL,
  `manager_task_id` binary(16) DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rg_workflow_open_piece` (`open_key`),
  KEY `idx_rg_workflow_piece` (`piece_id`),
  KEY `idx_rg_workflow_interpreter` (`interpreter_id`),
  KEY `idx_rg_workflow_status` (`status`),
  KEY `FKgoc5lyeg5eammadr5gtsxljid` (`assigned_by_id`),
  KEY `FKp6h5xb15ajlmh1njbi7ucrc5q` (`interpreter_task_id`),
  KEY `FK4qcy090nurc6o6dj26s5ir5aq` (`manager_task_id`),
  CONSTRAINT `FK4qcy090nurc6o6dj26s5ir5aq` FOREIGN KEY (`manager_task_id`) REFERENCES `tasks` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FK6428l9557233apd2kuy8iakn5` FOREIGN KEY (`interpreter_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK8yhfp0ffb9b96nukqw7x4gddv` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKgoc5lyeg5eammadr5gtsxljid` FOREIGN KEY (`assigned_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKp6h5xb15ajlmh1njbi7ucrc5q` FOREIGN KEY (`interpreter_task_id`) REFERENCES `tasks` (`id`) ON DELETE SET NULL,
  CONSTRAINT `radiography_workflows_chk_1` CHECK ((`status` in (_utf8mb4'INTERPRETER_ASSIGNED',_utf8mb4'REPORT_IN_PROGRESS',_utf8mb4'MANAGER_REVIEW',_utf8mb4'REINTERPRETATION_REQUIRED',_utf8mb4'MANAGER_APPROVED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `system_notification_reads` (
  `id` binary(16) NOT NULL,
  `dismissed` bit(1) NOT NULL,
  `read_at` datetime(6) NOT NULL,
  `notification_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_notification_read_user` (`notification_id`,`user_id`),
  KEY `FKrnkdotb7wnwj57kfgjw1w2xjb` (`user_id`),
  CONSTRAINT `FKefgm0phk08v68cofws1dy8eyh` FOREIGN KEY (`notification_id`) REFERENCES `system_notifications` (`id`),
  CONSTRAINT `FKrnkdotb7wnwj57kfgjw1w2xjb` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `system_notifications` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_number` int DEFAULT NULL,
  `project_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `piece_id` binary(16) DEFAULT NULL,
  `tensile_test_id` binary(16) DEFAULT NULL,
  `dedupe_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_id` binary(16) DEFAULT NULL,
  `task_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK93cc6rwy59ghcb89md3ukwynh` (`tensile_test_id`),
  KEY `FK4etgmt6ovsv2f3kyx5bb56hbd` (`piece_id`),
  KEY `idx_system_notifications_recipient_created` (`recipient_id`,`created_at`),
  KEY `idx_system_notifications_task` (`task_id`),
  KEY `idx_system_notifications_dedupe` (`recipient_id`,`dedupe_key`),
  CONSTRAINT `FK4etgmt6ovsv2f3kyx5bb56hbd` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`),
  CONSTRAINT `FK8qc5qem65dg2gx2unshilcrvb` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKe1mh3povy24gokmc0pl0ss2nf` FOREIGN KEY (`tensile_test_id`) REFERENCES `tensile_tests` (`id`),
  CONSTRAINT `FKqnoo8w7v7t9l538j7h06bjtp0` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_activities` (
  `id` binary(16) NOT NULL,
  `action` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `new_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_id` binary(16) NOT NULL,
  `task_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_activities_task_created` (`task_id`,`created_at`),
  KEY `idx_task_activities_actor` (`actor_id`),
  KEY `idx_task_activities_action_created` (`action`,`created_at`),
  CONSTRAINT `FKkgc6x6jdghxwivve0euo67sev` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKorrw5sneuadasoivv5e4f1chy` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `task_activities_chk_1` CHECK ((`action` in (_utf8mb4'CREATED',_utf8mb4'ASSIGNED',_utf8mb4'ASSIGNEE_CHANGED',_utf8mb4'TITLE_CHANGED',_utf8mb4'DESCRIPTION_CHANGED',_utf8mb4'PRIORITY_CHANGED',_utf8mb4'DEADLINE_CHANGED',_utf8mb4'PROJECT_CHANGED',_utf8mb4'PART_CHANGED',_utf8mb4'STATUS_CHANGED',_utf8mb4'COMPLETED',_utf8mb4'REOPENED',_utf8mb4'CANCELLED',_utf8mb4'COMMENT_ADDED',_utf8mb4'COMMENT_DELETED',_utf8mb4'CHECKLIST_ITEM_ADDED',_utf8mb4'CHECKLIST_ITEM_COMPLETED',_utf8mb4'CHECKLIST_ITEM_REOPENED',_utf8mb4'CHECKLIST_ITEM_REMOVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_checklist_items` (
  `id` binary(16) NOT NULL,
  `completed` bit(1) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `sort_order` int NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `completed_by` binary(16) DEFAULT NULL,
  `task_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_checklist_task_order` (`task_id`,`sort_order`),
  KEY `FK4noi2hr7x7iv1o8cra1osagua` (`completed_by`),
  CONSTRAINT `FK4noi2hr7x7iv1o8cra1osagua` FOREIGN KEY (`completed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKdss8rgar5gq2b5vjnjbapn4cu` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_comments` (
  `id` binary(16) NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `author_id` binary(16) NOT NULL,
  `task_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_comments_task_created` (`task_id`,`created_at`),
  KEY `idx_task_comments_author` (`author_id`),
  CONSTRAINT `FK9517viwn2geh1gpivj6l9y64u` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKtbd7uwo21s4shik5f0mghfk21` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tasks` (
  `id` binary(16) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `due_date` datetime(6) DEFAULT NULL,
  `priority` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `assigned_to` binary(16) NOT NULL,
  `created_by` binary(16) NOT NULL,
  `part_id` binary(16) DEFAULT NULL,
  `project_id` binary(16) DEFAULT NULL,
  `task_type` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `workflow_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tasks_assigned_to` (`assigned_to`),
  KEY `idx_tasks_created_by` (`created_by`),
  KEY `idx_tasks_status` (`status`),
  KEY `idx_tasks_priority` (`priority`),
  KEY `idx_tasks_due_date` (`due_date`),
  KEY `idx_tasks_project` (`project_id`),
  KEY `idx_tasks_part` (`part_id`),
  KEY `idx_tasks_assignee_status_due` (`assigned_to`,`status`,`due_date`),
  KEY `idx_tasks_status_due` (`status`,`due_date`),
  CONSTRAINT `FK2vjo8mbre3rvpbd6e7976b54m` FOREIGN KEY (`assigned_to`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9dgm9t7wn4w3gh57h63g712lo` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKcfaq3oc7k72suc1qdokksik2w` FOREIGN KEY (`part_id`) REFERENCES `pieces` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKsfhn82y57i3k9uxww1s007acc` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`) ON DELETE SET NULL,
  CONSTRAINT `tasks_chk_1` CHECK ((`priority` in (_utf8mb4'LOW',_utf8mb4'MEDIUM',_utf8mb4'HIGH',_utf8mb4'CRITICAL'))),
  CONSTRAINT `tasks_chk_2` CHECK ((`status` in (_utf8mb4'TODO',_utf8mb4'IN_PROGRESS',_utf8mb4'WAITING',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tensile_test_dispositions` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `disposition_date` date NOT NULL,
  `duration_hours` decimal(6,2) DEFAULT NULL,
  `inspection_result` enum('ACCEPTED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `next_stage` enum('AGING','APPROVED','CONCESSION_APPROVAL','NEXT_TENSILE','OTHER','RE_OPERATION','SCRAP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `quantity` int DEFAULT NULL,
  `system_generated` bit(1) NOT NULL,
  `temperature_c` decimal(7,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `responsible_user_id` binary(16) DEFAULT NULL,
  `tensile_test_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tensile_disposition_test` (`tensile_test_id`),
  KEY `FK3xdhw3kkhpehn4s11n3mygxk3` (`responsible_user_id`),
  CONSTRAINT `FK19gr234t9ng9ekjlj917ukc36` FOREIGN KEY (`tensile_test_id`) REFERENCES `tensile_tests` (`id`),
  CONSTRAINT `FK3xdhw3kkhpehn4s11n3mygxk3` FOREIGN KEY (`responsible_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tensile_test_samples` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `el` decimal(10,2) DEFAULT NULL,
  `hb` decimal(10,2) DEFAULT NULL,
  `sample_index` int NOT NULL,
  `sample_label` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uts` decimal(10,2) DEFAULT NULL,
  `ys` decimal(10,2) DEFAULT NULL,
  `tensile_test_id` binary(16) NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tensile_sample_index` (`tensile_test_id`,`sample_index`),
  CONSTRAINT `FKayh504cx5t8t60o221eawdsm0` FOREIGN KEY (`tensile_test_id`) REFERENCES `tensile_tests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tensile_tests` (
  `id` binary(16) NOT NULL,
  `below_threshold` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('APPROVED','PENDING_REVIEW','REJECTED','RETEST_REQUIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `test_index` int NOT NULL,
  `threshold_failures` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tensile_piece_index` (`piece_id`,`test_index`),
  CONSTRAINT `FKnd3e58hysh8camrjo67v6t3ur` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_permissions` (
  `user_id` binary(16) NOT NULL,
  `permission` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UKo090f18qtl3dv36wdih9fm3k8` (`user_id`,`permission`),
  CONSTRAINT `FKkowxl8b2bngrxd1gafh13005u` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `full_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `disabled` bit(1) NOT NULL,
  `last_activity_at` datetime(6) DEFAULT NULL,
  `signature_content_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `signature_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `signature_updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `welding_map_marks` (
  `drawing_revision_id` BINARY(16) NULL,
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `defect_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `x_percent` double NOT NULL,
  `y_percent` double NOT NULL,
  `piece_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKebt27fq7jj3k9okr6w83xffq5` (`piece_id`),
  CONSTRAINT `FKebt27fq7jj3k9okr6w83xffq5` FOREIGN KEY (`piece_id`) REFERENCES `pieces` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2026-10-31 additive scrap feature extension to the inspected baseline.
-- Additive only. Does not invent reasons or modify existing scrapped pieces/history.
CREATE TABLE IF NOT EXISTS piece_scrap_dispatches (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    piece_id BINARY(16) NOT NULL,
    active_piece_key BINARY(16) NULL,
    defect_type VARCHAR(255) NOT NULL,
    defect_type_custom VARCHAR(255) NULL,
    notes TEXT NULL,
    sent_at DATETIME(6) NULL,
    sent_by VARCHAR(255) NULL,
    origin_stage VARCHAR(32) NULL,
    origin_sub_stage VARCHAR(32) NULL,
    reversed_at DATETIME(6) NULL,
    reversed_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_scrap_active_piece UNIQUE (active_piece_key),
    INDEX idx_scrap_piece_sent (piece_id, sent_at),
    CONSTRAINT fk_scrap_piece FOREIGN KEY (piece_id) REFERENCES pieces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS piece_scrap_dispatch_defects (
    dispatch_id BINARY(16) NOT NULL,
    defect_id BINARY(16) NOT NULL,
    PRIMARY KEY (dispatch_id, defect_id),
    CONSTRAINT fk_scrap_link_dispatch FOREIGN KEY (dispatch_id) REFERENCES piece_scrap_dispatches(id),
    CONSTRAINT fk_scrap_link_defect FOREIGN KEY (defect_id) REFERENCES radiography_defects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_access_events (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_id BINARY(16) NULL,
    actor_username VARCHAR(255) NOT NULL,
    target_id BINARY(16) NOT NULL,
    action VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    before_state TEXT NOT NULL,
    after_state TEXT NOT NULL,
    INDEX idx_user_access_target_time (target_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS heat_batch_events (
 id BINARY(16) NOT NULL PRIMARY KEY,
 batch_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NOT NULL,
 actor_name VARCHAR(255) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 before_state LONGTEXT NOT NULL,
 after_state LONGTEXT NOT NULL,
 affected_pieces LONGTEXT NOT NULL,
 INDEX idx_heat_batch_event_time(batch_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Independent normal-advance and reasoned forward-skip access.
-- No existing user's permissions are granted/regranted automatically.
CREATE TABLE IF NOT EXISTS piece_stage_skips (
    id BINARY(16) NOT NULL PRIMARY KEY,
    piece_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    actor_name VARCHAR(255) NOT NULL,
    from_stage VARCHAR(32) NOT NULL,
    from_sub_stage VARCHAR(64) NULL,
    to_stage VARCHAR(32) NOT NULL,
    to_sub_stage VARCHAR(64) NULL,
    reason VARCHAR(1000) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    INDEX idx_stage_skip_piece_time (piece_id, occurred_at),
    CONSTRAINT fk_stage_skip_piece FOREIGN KEY (piece_id) REFERENCES pieces(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS record_edit_events (
 id BINARY(16) NOT NULL PRIMARY KEY,
 kind VARCHAR(30) NOT NULL,
 target_id BINARY(16) NOT NULL,
 actor_id BINARY(16) NOT NULL,
 actor_username VARCHAR(255) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,
 before_state LONGTEXT NOT NULL,
 after_state LONGTEXT NOT NULL,
 INDEX idx_record_edit_target (kind,target_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS drawing_revisions (
 id BINARY(16) NOT NULL PRIMARY KEY,
 definition_id BINARY(16) NOT NULL,
 revision_number INT NOT NULL,
 relative_path VARCHAR(255) NOT NULL,
 original_name VARCHAR(255) NULL,
 uploaded_at DATETIME(6) NULL,
 captured_at DATETIME(6) NOT NULL,
 uploaded_by VARCHAR(255) NULL,
 source_revision_id BINARY(16) NULL,
 UNIQUE KEY uk_drawing_revision_number(definition_id,revision_number),
 CONSTRAINT fk_drawing_revision_definition FOREIGN KEY(definition_id) REFERENCES piece_definitions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
