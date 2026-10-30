package com.factory.productmanager.task;

import com.factory.productmanager.config.LegacyDefectChainRepair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** Runs on a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named = "radiography.positions.mysql.enabled", matches = "true")
class RadiographyPositionMigrationMysqlTest {
    private void migrate(Connection connection) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/V34__radiography_text_positions.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.stripLeading().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            try (var statement = connection.createStatement()) {
                for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    @Test void widensNumericColumnsPreservesValuesAndSupportsLegacyRepairWithText() throws Exception {
        try (var connection = com.factory.productmanager.support.IsolatedMysql.connect("radiography_positions_synthetic");
             var statement = connection.createStatement()) {
            assertEquals("radiography_positions_synthetic", connection.getCatalog());
            statement.execute("CREATE TABLE radiography_defects (id INT PRIMARY KEY,parent_defect_id INT NULL,"
                    + "start_cm INT NULL,end_cm INT NULL,location_cm INT NULL,status VARCHAR(32),welded BIT NOT NULL DEFAULT 0) DEFAULT CHARSET=utf8mb4");
            statement.execute("CREATE TABLE radiography_defect_positions (defect_id INT,position_order INT,"
                    + "start_cm INT NOT NULL,end_cm INT NOT NULL,PRIMARY KEY(defect_id,position_order)) DEFAULT CHARSET=utf8mb4");
            statement.execute("INSERT INTO radiography_defects (id,start_cm,end_cm,location_cm,status) VALUES "
                    + "(1,12,20,12,'APPROVED'),(2,NULL,NULL,25,'APPROVED'),(3,0,0,0,'APPROVED')");
            statement.execute("INSERT INTO radiography_defect_positions VALUES (1,0,12,20)");
            migrate(connection);
            migrate(connection);
            try (var rows = statement.executeQuery("SELECT start_cm,end_cm FROM radiography_defect_positions WHERE defect_id=1")) {
                assertTrue(rows.next()); assertEquals("12", rows.getString(1)); assertEquals("20", rows.getString(2));
            }
            statement.execute("INSERT INTO radiography_defects (id,start_cm,end_cm,location_cm,status) "
                    + "VALUES (4,'L4','L7','L4','NEEDS_WELDING'),(5,'محل دریچه','محل دریچه','محل دریچه','APPROVED')");
            var repair = new LegacyDefectChainRepair(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            repair.run(new DefaultApplicationArguments());
            repair.run(new DefaultApplicationArguments());
            migrate(connection);
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM radiography_defect_positions")) {
                assertTrue(rows.next()); assertEquals(5, rows.getInt(1));
            }
            for (var expected : new String[][]{{"1", "12", "20"}, {"2", "25", "25"}, {"3", "0", "0"},
                    {"4", "L4", "L7"}, {"5", "محل دریچه", "محل دریچه"}}) {
                try (var rows = statement.executeQuery("SELECT start_cm,end_cm FROM radiography_defect_positions WHERE defect_id=" + expected[0])) {
                    assertTrue(rows.next()); assertEquals(expected[1], rows.getString(1)); assertEquals(expected[2], rows.getString(2));
                }
            }
            try (var rows = statement.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME IN ('start_cm','end_cm','location_cm')")) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    assertEquals("varchar", rows.getString("DATA_TYPE"));
                    assertEquals(255, rows.getInt("CHARACTER_MAXIMUM_LENGTH"));
                    assertEquals(rows.getString("TABLE_NAME").equals("radiography_defects") ? "YES" : "NO", rows.getString("IS_NULLABLE"));
                }
                assertEquals(5, count);
            }
        }
    }
}
