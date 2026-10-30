package com.factory.productmanager.config;

import com.factory.productmanager.model.entity.TaskType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Reconcile old MySQL task-type ENUMs and Hibernate-generated enum CHECKs before new task types are written. */
@Component
@Order(2)
@RequiredArgsConstructor
public class TaskTypeSchemaRepair implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskTypeSchemaRepair.class);
    private static final Pattern ENUM_CHECK = Pattern.compile(
            "^[\\s(]*task_type\\s+in\\s*\\((?:\\s*(?:_[a-z0-9]+)?'[A-Z0-9_]+'\\s*,?)+\\)[\\s)]*$",
            Pattern.CASE_INSENSITIVE);
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        }
        var checks = jdbc.query("""
                SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
                FROM information_schema.TABLE_CONSTRAINTS tc
                JOIN information_schema.CHECK_CONSTRAINTS cc
                  ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='tasks' AND tc.CONSTRAINT_TYPE='CHECK'
                """, (row, index) -> new String[]{row.getString(1), row.getString(2)});
        for (var check : checks) {
            String clause = check[1].replace("`", "").replace("\\'", "'");
            if (!ENUM_CHECK.matcher(clause).matches()) continue; // Never touch mixed/business CHECKs.
            boolean current = Arrays.stream(TaskType.values()).allMatch(type -> clause.contains("'" + type.name() + "'"));
            if (current) continue;
            jdbc.execute("ALTER TABLE tasks DROP CHECK `" + check[0].replace("`", "``") + "`");
            log.info("Removed outdated task-type enum CHECK; application enum validation remains active");
        }
        var types = jdbc.query("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND COLUMN_NAME='task_type'
                """, (row, index) -> row.getString(1));
        if (!types.isEmpty() && "enum".equalsIgnoreCase(types.get(0))) {
            jdbc.execute("ALTER TABLE tasks MODIFY COLUMN task_type VARCHAR(48) NULL");
            log.info("Converted legacy tasks.task_type ENUM to nullable VARCHAR(48)");
        }
    }
}
