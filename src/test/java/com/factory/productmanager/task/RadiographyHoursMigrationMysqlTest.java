package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mysql.tests.enabled", matches = "true")
class RadiographyHoursMigrationMysqlTest {
    @Test
    void addsNullableDurationsAndPreservesBothLegacyAndNewValuesOnRepeat() throws Exception {
        String schema = "radiography_hours_upgrade_synthetic";
        try (var connection = IsolatedMysql.connect(schema); var statement = connection.createStatement()) {
            IsolatedMysql.assertOwned(connection, schema);
            statement.execute("CREATE TABLE piece_definitions (id INT PRIMARY KEY, piece_name VARCHAR(40) NOT NULL)");
            statement.execute("INSERT INTO piece_definitions VALUES (1, 'Legacy row'), (2, 'Configured row')");

            var migration = new ClassPathResource("db/migration/V43__radiography_standard_hours.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            statement.executeUpdate("""
                    UPDATE piece_definitions
                    SET radiography_shot_hours = 1.25, radiography_interpretation_hours = 0.75
                    WHERE id = 2
                    """);
            ScriptUtils.executeSqlScript(connection, migration);

            try (var rows = statement.executeQuery("""
                    SELECT piece_name, radiography_shot_hours, radiography_interpretation_hours
                    FROM piece_definitions ORDER BY id
                    """)) {
                assertTrue(rows.next());
                assertEquals("Legacy row", rows.getString(1));
                assertNull(rows.getBigDecimal(2));
                assertNull(rows.getBigDecimal(3));
                assertTrue(rows.next());
                assertEquals("Configured row", rows.getString(1));
                assertEquals(new BigDecimal("1.25"), rows.getBigDecimal(2));
                assertEquals(new BigDecimal("0.75"), rows.getBigDecimal(3));
                assertFalse(rows.next());
            }
        }
    }
}
