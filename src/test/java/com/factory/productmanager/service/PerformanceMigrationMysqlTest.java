package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** No application startup or production configuration: fresh additive DDL + Hibernate validation. */
@EnabledIfSystemProperty(named = "performance.mysql.enabled", matches = "true")
class PerformanceMigrationMysqlTest {
    private String url() { return com.factory.productmanager.support.IsolatedMysql.url("performance_ddl_synthetic"); }
    private Configuration configuration(String action, boolean includePerformance) {
        var cfg = new Configuration().addAnnotatedClass(User.class);
        if (includePerformance) cfg.addAnnotatedClass(PerformancePeriod.class).addAnnotatedClass(PerformanceCriterion.class).addAnnotatedClass(PerformanceEvent.class);
        cfg.setProperty("hibernate.connection.url", url());
        cfg.setProperty("hibernate.connection.username", com.factory.productmanager.support.IsolatedMysql.username()); cfg.setProperty("hibernate.connection.password", com.factory.productmanager.support.IsolatedMysql.password());
        cfg.setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
        cfg.setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        cfg.setProperty("hibernate.hbm2ddl.auto", action); cfg.setProperty("hibernate.hbm2ddl.halt_on_error", "true");
        return cfg;
    }
    @Test void additiveMigrationReplaysAndMatchesJpaAndProtectsOpenKeyAndOwnership() throws Exception {
        try (var ignored = configuration("update", false).buildSessionFactory()) { /* Create only the users prerequisite. */ }
        try (var connection = com.factory.productmanager.support.IsolatedMysql.connect("performance_ddl_synthetic")) {
            com.factory.productmanager.support.IsolatedMysql.assertOwned(connection,"performance_ddl_synthetic");
            apply(connection); apply(connection);
            apply(connection, "V44__performance_bonus_efficiency.sql");
            try (var ignored = configuration("validate", true).buildSessionFactory()) { /* Checks MySQL types against all four mappings. */ }
            // Both migrations also work after the current Hibernate mappings are applied.
            try (var ignored = configuration("update", true).buildSessionFactory()) { }
            apply(connection);
            apply(connection, "V44__performance_bonus_efficiency.sql");
            connection.setAutoCommit(false);
            try (var s = connection.createStatement()) {
                s.executeUpdate("INSERT INTO users(id,username,password,role,disabled,created_at) VALUES (UUID_TO_BIN(UUID()),CONCAT('ddl_',UUID()),'synthetic','OPERATOR',0,NOW(6))");
                byte[] userId;
                try (var r = s.executeQuery("SELECT id FROM users WHERE username LIKE 'ddl_%' ORDER BY created_at DESC LIMIT 1")) { assertTrue(r.next()); userId = r.getBytes(1); }
                try (var insert = connection.prepareStatement("INSERT INTO performance_periods(id,user_id,version,revision,title,open_user_key,created_at,updated_at) VALUES(UUID_TO_BIN(UUID()),?,0,0,'آبان ۱۴۰۵',BIN_TO_UUID(?),NOW(6),NOW(6))")) {
                    insert.setBytes(1, userId); insert.setBytes(2, userId); insert.executeUpdate();
                    var duplicate = assertThrows(SQLException.class, insert::executeUpdate); assertEquals(1062, duplicate.getErrorCode());
                    try (var close = connection.prepareStatement("UPDATE performance_periods SET open_user_key=NULL,closed_at=NOW(6) WHERE user_id=?")) { close.setBytes(1,userId); close.executeUpdate(); }
                    assertEquals(1, insert.executeUpdate());
                }
                assertEquals(1, s.executeUpdate("""
                        INSERT INTO performance_events
                            (id, action, actor_id, actor_username, occurred_at, period_id, old_efficiency, new_efficiency)
                        SELECT UUID_TO_BIN(UUID()), 'CRITERION_UPDATED', user_id, 'synthetic', NOW(6), id, 0.8500, 999999999999.0000
                        FROM performance_periods WHERE open_user_key IS NOT NULL LIMIT 1
                        """));
                try (var scores = s.executeQuery("SELECT old_efficiency, new_efficiency FROM performance_events WHERE actor_username='synthetic'")) {
                    assertTrue(scores.next());
                    assertEquals(new java.math.BigDecimal("0.8500"), scores.getBigDecimal(1));
                    assertEquals(new java.math.BigDecimal("999999999999.0000"), scores.getBigDecimal(2));
                }
                try (var delete = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
                    delete.setBytes(1, userId); assertEquals(1451, assertThrows(SQLException.class, delete::executeUpdate).getErrorCode());
                }
            } finally { connection.rollback(); }
        }
    }
    private void apply(Connection connection) throws Exception {
        apply(connection, "V31__employee_performance.sql");
    }

    private void apply(Connection connection, String migration) throws Exception {
        var sql = Files.readString(Path.of("src/main/resources/db/migration", migration));
        sql = sql.replaceAll("(?m)^--.*$", "");
        for (String statement : sql.split(";")) if (!statement.isBlank()) {
            try (var s = connection.createStatement()) { s.execute(statement); }
        }
    }
}
