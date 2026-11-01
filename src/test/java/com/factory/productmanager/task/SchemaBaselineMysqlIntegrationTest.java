package com.factory.productmanager.task;

import com.factory.productmanager.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/** Loads the inspected record-free schema, validates JPA, then exercises real API writes. */
@EnabledIfSystemProperty(named="mysql.tests.enabled", matches="true")
@ActiveProfiles("mysql-schema")
@ContextConfiguration(initializers=MysqlTestContextInitializer.class)
class SchemaBaselineMysqlIntegrationTest extends MeltWorkflowIntegrationTest {
    @Test void importedSchemaHasAllTablesAndForeignKeysAndNoProductionMigrationHistory() throws Exception {
        try (var c = IsolatedMysql.connect("schema_baseline_synthetic"); var s = c.createStatement()) {
            IsolatedMysql.assertOwned(c, "schema_baseline_synthetic");
            try (var r = s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                assertTrue(r.next()); assertEquals(50, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()")) {
                assertTrue(r.next()); assertEquals(66, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
                assertTrue(r.next()); assertEquals(0, r.getInt(1));
            }
        }
    }
}
