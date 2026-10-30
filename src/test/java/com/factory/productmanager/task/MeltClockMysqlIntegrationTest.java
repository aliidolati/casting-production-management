package com.factory.productmanager.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** Runs on a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named="melt.mysql.enabled",matches="true")
class MeltClockMysqlIntegrationTest {
    static void applyMigration(Connection connection) throws Exception {
        try (var input=MeltClockMysqlIntegrationTest.class.getResourceAsStream("/db/migration/V30__melt_610_clock.sql")) {
            assertNotNull(input);
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.stripLeading().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
            try (var statement=connection.createStatement()) {
                for (String command:sql.split(";")) if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    @Test void additiveClockMigrationPreservesLegacyDecimalsAndCanRunTwice() throws Exception {
        try (var connection=com.factory.productmanager.support.IsolatedMysql.connect("melt_clock_synthetic");var statement=connection.createStatement()) {
            assertEquals("melt_clock_synthetic",connection.getCatalog());
            statement.execute("CREATE TABLE melt_passports (id INT PRIMARY KEY, time_at610 DECIMAL(24,8) NULL)");
            statement.execute("INSERT INTO melt_passports VALUES (1,15.75),(2,NULL)");
            applyMigration(connection);applyMigration(connection);
            try (var rows=statement.executeQuery("SELECT time_at610,time_at610_clock FROM melt_passports WHERE id=1")) {
                assertTrue(rows.next());assertEquals(0,new BigDecimal("15.75").compareTo(rows.getBigDecimal(1)));assertNull(rows.getTime(2));
            }
            try (var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"melt_passports","time_at610_clock")) {
                assertTrue(columns.next());assertEquals(Types.TIME,columns.getInt("DATA_TYPE"));
                assertEquals(DatabaseMetaData.columnNullable,columns.getInt("NULLABLE"));
            }
            statement.execute("UPDATE melt_passports SET time_at610_clock='14:50:00' WHERE id=1");
            applyMigration(connection);
            try (var rows=statement.executeQuery("SELECT time_at610,time_at610_clock FROM melt_passports WHERE id=1")) {
                assertTrue(rows.next());assertEquals("14:50:00",rows.getTime(2).toString());
                assertEquals(0,new BigDecimal("15.75").compareTo(rows.getBigDecimal(1)));
            }
        }
    }
}
