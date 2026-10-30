package com.factory.productmanager.task;

import com.factory.productmanager.config.TaskTypeSchemaRepair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs on a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named="melt.tasktype.mysql.enabled",matches="true")
class MeltTaskTypeMigrationMysqlTest {
    @Test void oldEnumAndCheckReproduceConflictAndBothUpgradePathsPreserveLegacyRows() throws Exception {
        try (var c = com.factory.productmanager.support.IsolatedMysql.connect("melt_tasktype_synthetic")) {
            assertEquals("melt_tasktype_synthetic", c.getCatalog());
            for (boolean migration : new boolean[]{true,false}) for (boolean nativeEnum : new boolean[]{true,false}) {
                try (var s = c.createStatement()) {
                    s.execute("DROP TABLE IF EXISTS tasks");
                    String codes = "'GENERAL','RADIOGRAPHY_INTERPRETATION','RADIOGRAPHY_MANAGER_APPROVAL'";
                    s.execute("CREATE TABLE tasks(id INT PRIMARY KEY, task_type " + (nativeEnum ? "ENUM("+codes+")" : "VARCHAR(48)")
                            + " NULL, value INT NOT NULL, CONSTRAINT business_check CHECK(value>=0)"
                            + ", CONSTRAINT current_type_check CHECK(task_type IN ("+codes+",'MELT_PASSPORT_ENTRY','MELT_PASSPORT_APPROVAL'))"
                            + ", CONSTRAINT mixed_business_check CHECK(task_type IS NULL OR value<100)"
                            + (nativeEnum ? "" : ", CONSTRAINT old_type_check CHECK(task_type IN ("+codes+"))") + ")");
                    s.execute("INSERT INTO tasks VALUES(1,NULL,1),(2,'RADIOGRAPHY_INTERPRETATION',2)");
                    var rejected = assertThrows(SQLException.class, () -> s.execute("INSERT INTO tasks VALUES(3,'MELT_PASSPORT_ENTRY',3)"));
                    assertEquals(nativeEnum ? 1265 : 3819, rejected.getErrorCode());
                    if (migration) { migrate(c); migrate(c); }
                    else {
                        var repair = new TaskTypeSchemaRepair(new JdbcTemplate(new SingleConnectionDataSource(c,true)));
                        repair.run(null); repair.run(null);
                    }
                    s.execute("INSERT INTO tasks VALUES(3,'MELT_PASSPORT_ENTRY',3),(4,'MELT_PASSPORT_APPROVAL',4)");
                    assertThrows(SQLException.class, () -> s.execute("INSERT INTO tasks VALUES(5,'GENERAL',-1)"));
                    assertThrows(SQLException.class, () -> s.execute("INSERT INTO tasks VALUES(5,'GENERAL',100)"));
                    try (var rows = s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_TYPE='CHECK'")) {
                        assertTrue(rows.next()); assertEquals(3, rows.getInt(1));
                    }
                    try (var rows = s.executeQuery("SELECT task_type FROM tasks ORDER BY id")) {
                        assertTrue(rows.next()); assertNull(rows.getString(1));
                        assertTrue(rows.next()); assertEquals("RADIOGRAPHY_INTERPRETATION",rows.getString(1));
                        assertTrue(rows.next()); assertEquals("MELT_PASSPORT_ENTRY",rows.getString(1));
                        assertTrue(rows.next()); assertEquals("MELT_PASSPORT_APPROVAL",rows.getString(1));
                        assertFalse(rows.next());
                    }
                }
            }
        }
    }

    private void migrate(Connection c) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V36__task_type_melt_compatibility.sql"); var s=c.createStatement()) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(),StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.stripLeading().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
            for (String command : sql.split(";")) if (!command.isBlank()) s.execute(command);
        }
    }
}
