package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import com.factory.productmanager.support.MysqlTestContextInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mysql.tests.enabled", matches = "true")
@ActiveProfiles("spectrometry-mysql")
@ContextConfiguration(initializers = MysqlTestContextInitializer.class)
class SpectrometryMysqlIntegrationTest extends SpectrometryApiContract {

    @Test
    void standaloneMigrationPreservesExistingPiecesAndResultsAndEnforcesForeignKey() throws Exception {
        String schema = "spectrometry_upgrade_synthetic";
        try (var connection = IsolatedMysql.connect(schema); var sql = connection.createStatement()) {
            IsolatedMysql.assertOwned(connection, schema);
            sql.execute("CREATE TABLE pieces (id BINARY(16) PRIMARY KEY, piece_number INT NOT NULL)");
            sql.execute("INSERT INTO pieces VALUES (UUID_TO_BIN('00000000-0000-0000-0000-000000000009'), 9)");
            var migration = new ClassPathResource("db/migration/V47__spectrometry.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            String insert = """
                    INSERT INTO spectrometry_tests (id,version,piece_id,test_date,al,si,mg,cu,fe,mn,zn,ti,created_at,updated_at)
                    SELECT UUID_TO_BIN(UUID()),0,id,'2026-01-01',92,7,0.1234567891,0,0,0,0,0,NOW(6),NOW(6) FROM pieces
                    """;
            sql.execute(insert);
            sql.execute(insert);
            ScriptUtils.executeSqlScript(connection, migration);
            try (var result = sql.executeQuery("""
                    SELECT COUNT(*), MIN(mg), MAX(piece_number) FROM spectrometry_tests JOIN pieces ON pieces.id=piece_id
                    """)) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
                assertEquals(new BigDecimal("0.1234567891"), result.getBigDecimal(2));
                assertEquals(9, result.getInt(3));
            }
            assertEquals(1451, assertThrows(SQLException.class, () -> sql.execute("DELETE FROM pieces")).getErrorCode());
        }
    }
}
