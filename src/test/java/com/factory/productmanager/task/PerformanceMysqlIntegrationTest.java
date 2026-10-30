package com.factory.productmanager.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named = "performance.mysql.enabled", matches = "true")
@org.springframework.test.context.ContextConfiguration(initializers=com.factory.productmanager.support.MysqlTestContextInitializer.class)
@ActiveProfiles("performance-mysql")
class PerformanceMysqlIntegrationTest extends PerformanceApiIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void datasourceIsTheDedicatedSyntheticInstance() {
        assertTrue(jdbc.execute((java.sql.Connection c) -> c.getMetaData().getURL())
                .equals(com.factory.productmanager.support.IsolatedMysql.url("performance_synthetic")));
    }
    @Test @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void detailUsesCurrentCriterionValuesEvenAfterAnOlderRepeatableReadSnapshot() throws Exception {
        // MySQL-specific snapshot/locking semantics; H2 rejects this scenario with a serialization failure.
        assertCurrentCriterionValuesAfterAnOlderSnapshot();
    }
}
