package com.factory.productmanager.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs on a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named="melt.correction.mysql.enabled",matches="true")
class MeltCorrectionMigrationMysqlTest {
    void migrate(Connection connection) throws Exception {
        try(var input=getClass().getResourceAsStream("/db/migration/V32__melt_passport_correction_audit.sql")) {
            assertNotNull(input);
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(l->!l.stripLeading().startsWith("--"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            try(var statement=connection.createStatement()) {
                for(String command:sql.split(";")) if(!command.isBlank()) statement.execute(command);
            }
        }
    }
    @Test void v32PreservesLegacyEventsAndUtf8SnapshotsAcrossRepeatedRuns() throws Exception {
        try(var connection=com.factory.productmanager.support.IsolatedMysql.connect("melt_correction_synthetic");var statement=connection.createStatement()) {
            assertEquals("melt_correction_synthetic",connection.getCatalog());
            statement.execute("CREATE TABLE melt_passport_events (id INT PRIMARY KEY,action VARCHAR(32) NOT NULL,reason VARCHAR(2000) NULL) DEFAULT CHARSET=utf8mb4");
            statement.execute("INSERT INTO melt_passport_events VALUES (1,'APPROVED','سابقه قدیمی')");
            migrate(connection);migrate(connection);
            try(var rows=statement.executeQuery("SELECT action,reason,old_values,new_values FROM melt_passport_events WHERE id=1")) {
                assertTrue(rows.next());assertEquals("APPROVED",rows.getString(1));assertEquals("سابقه قدیمی",rows.getString(2));assertNull(rows.getString(3));assertNull(rows.getString(4));
            }
            String oldValues="{\"notes\":\"مقدار قدیمی\"}";
            String newValues="{\"notes\":\""+"متن فارسی <script>آزمایشی</script> ".repeat(200)+"\"}";
            try(var insert=connection.prepareStatement("INSERT INTO melt_passport_events (id,action,old_values,new_values) VALUES (2,'ADMIN_CORRECTED',?,?)")) {
                insert.setString(1,oldValues);insert.setString(2,newValues);insert.executeUpdate();
            }
            migrate(connection);
            try(var rows=statement.executeQuery("SELECT old_values,new_values FROM melt_passport_events WHERE id=2")) {
                assertTrue(rows.next());assertEquals(oldValues,rows.getString(1));assertEquals(newValues,rows.getString(2));
            }
            try(var rows=statement.executeQuery("SELECT DATA_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='melt_passport_events' AND COLUMN_NAME IN ('old_values','new_values')")) {
                int count=0;while(rows.next()) {count++;assertEquals("mediumtext",rows.getString(1));assertEquals("YES",rows.getString(2));}assertEquals(2,count);
            }
        }
    }
}
