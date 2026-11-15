package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mysql.tests.enabled", matches = "true")
class WeldingCompletionMigrationMysqlTest {
    @Test
    void upgradeKeepsLegacyRowsUnclassifiedAndPreservesNewResultsOnRepeat() throws Exception {
        String schema = "welding_completion_upgrade_synthetic";
        try (var connection = IsolatedMysql.connect(schema); var statement = connection.createStatement()) {
            IsolatedMysql.assertOwned(connection, schema);
            statement.execute("CREATE TABLE piece_welding_dispatches (id INT PRIMARY KEY, completed_at DATETIME(6))");
            statement.execute("INSERT INTO piece_welding_dispatches VALUES (1, '2026-01-01 09:00:00')");
            var migration = new ClassPathResource("db/migration/V45__welding_process_completion.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            statement.execute("""
                    INSERT INTO piece_welding_dispatches VALUES
                        (2, '2026-01-01 10:00:00', TRUE), (3, '2026-01-01 11:00:00', FALSE)
                    """);
            ScriptUtils.executeSqlScript(connection, migration);

            try (var rows = statement.executeQuery("""
                    SELECT process_completed FROM piece_welding_dispatches ORDER BY id
                    """)) {
                assertTrue(rows.next());
                assertNull(rows.getObject(1));
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
                assertTrue(rows.next());
                assertFalse(rows.getBoolean(1));
                assertFalse(rows.wasNull());
                assertFalse(rows.next());
            }
        }
    }
}
