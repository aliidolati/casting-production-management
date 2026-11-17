package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mysql.tests.enabled", matches = "true")
class RadiographyFreeTextMigrationMysqlTest {
    @Test
    void preservesLegacyRowsAndAllowsUncategorizedTextOnRepeat() throws Exception {
        String schema = "radiography_free_text_upgrade";
        try (var connection = IsolatedMysql.connect(schema); var statement = connection.createStatement()) {
            IsolatedMysql.assertOwned(connection, schema);
            statement.execute("""
                    CREATE TABLE radiography_defects (
                        id INT PRIMARY KEY,
                        defect_type VARCHAR(255) NOT NULL,
                        status ENUM('APPROVED', 'NEEDS_WELDING') NOT NULL
                    ) DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("INSERT INTO radiography_defects VALUES (1, 'F.M.L', 'NEEDS_WELDING')");
            var migration = new ClassPathResource("db/migration/V46__radiography_free_text_defects.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            String text = "L4 → ریشوت\nبررسی شد 🙂";
            try (var insert = connection.prepareStatement(
                    "INSERT INTO radiography_defects (id, entry_mode, free_text) VALUES (2, 'FREE_TEXT', ?)")) {
                insert.setString(1, text);
                insert.executeUpdate();
            }
            ScriptUtils.executeSqlScript(connection, migration);
            try (var rows = statement.executeQuery("SELECT * FROM radiography_defects ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals("STRUCTURED", rows.getString("entry_mode"));
                assertEquals("F.M.L", rows.getString("defect_type"));
                assertEquals("NEEDS_WELDING", rows.getString("status"));
                assertNull(rows.getString("free_text"));
                assertTrue(rows.next());
                assertEquals("FREE_TEXT", rows.getString("entry_mode"));
                assertEquals(text, rows.getString("free_text"));
                assertNull(rows.getString("defect_type"));
                assertNull(rows.getString("status"));
                assertFalse(rows.next());
            }
        }
    }
}
