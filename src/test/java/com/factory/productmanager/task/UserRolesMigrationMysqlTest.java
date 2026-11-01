package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="mysql.tests.enabled", matches="true")
class UserRolesMigrationMysqlTest {
    @Test void migrationRetainsLegacyAccountsAllowsManagerAndSurvivesRepeatExecution() throws Exception {
        try (var c=IsolatedMysql.connect("user_roles_upgrade_synthetic"); var s=c.createStatement()) {
            IsolatedMysql.assertOwned(c, "user_roles_upgrade_synthetic");
            s.execute("CREATE TABLE users(id INT PRIMARY KEY, role ENUM('ADMIN','OPERATOR') NOT NULL, name VARCHAR(80))");
            s.execute("INSERT INTO users VALUES(1,'ADMIN','legacy admin'),(2,'OPERATOR','legacy operator')");
            assertThrows(SQLException.class, () -> s.execute("UPDATE users SET role='MANAGER' WHERE id=1"));
            try (var input=getClass().getResourceAsStream("/db/migration/V38__user_roles_and_access_audit.sql")) {
                assertNotNull(input);
                String sql=new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .filter(line -> !line.stripLeading().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
                for (int i=0;i<2;i++) for (String command:sql.split(";")) if (!command.isBlank()) s.execute(command);
            }
            try (var rows=s.executeQuery("SELECT role FROM users ORDER BY id")) {
                assertTrue(rows.next());assertEquals("ADMIN",rows.getString(1));
                assertTrue(rows.next());assertEquals("OPERATOR",rows.getString(1));
            }
            s.execute("UPDATE users SET role='MANAGER' WHERE id=1");
            try (var rows=s.executeQuery("SELECT COUNT(*) FROM user_access_events")) {
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));
            }
        }
    }
}
