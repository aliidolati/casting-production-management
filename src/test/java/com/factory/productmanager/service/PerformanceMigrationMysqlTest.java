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
            try (var ignored = configuration("validate", true).buildSessionFactory()) { /* Checks MySQL types against all four mappings. */ }
            // The migration is also safe after Hibernate-created tables.
            try (var ignored = configuration("update", true).buildSessionFactory()) { }
            apply(connection);
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
                try (var delete = connection.prepareStatement("DELETE FROM users WHERE id=?")) {
                    delete.setBytes(1, userId); assertEquals(1451, assertThrows(SQLException.class, delete::executeUpdate).getErrorCode());
                }
            } finally { connection.rollback(); }
        }
    }
    private void apply(Connection connection) throws Exception {
        var sql = Files.readString(Path.of("src/main/resources/db/migration/V31__employee_performance.sql"));
        sql = sql.replaceAll("(?m)^--.*$", "");
        for (String statement : sql.split(";")) if (!statement.isBlank()) {
            try (var s = connection.createStatement()) { s.execute(statement); }
        }
    }
}
