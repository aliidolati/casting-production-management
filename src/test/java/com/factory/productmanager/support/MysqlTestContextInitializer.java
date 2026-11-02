package com.factory.productmanager.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.Arrays;

/** Overrides datasource inputs before Hibernate or startup repair beans can run. */
public class MysqlTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        var profiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
        String schema = profiles.contains("drawings-mysql") ? "drawings_synthetic"
                : profiles.contains("scrap-mysql") ? "scrap_workflow_synthetic"
                : profiles.contains("melt-mysql") ? "melt_lookup_synthetic"
                : profiles.contains("performance-mysql") ? "performance_synthetic"
                : profiles.contains("mysql-schema") ? "schema_baseline_synthetic" : "cloud_business_synthetic";
        if (profiles.contains("mysql-schema")) {
            try (var c = IsolatedMysql.connect(schema); var statement = c.createStatement()) {
                IsolatedMysql.assertOwned(c, schema);
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                    rows.next();
                    if (rows.getInt(1) != 0) throw new IllegalStateException("Schema baseline requires an empty owned database");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                try {
                    new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                            new org.springframework.core.io.ClassPathResource("db/mysql/schema-synthetic.sql")).populate(c);
                } finally { statement.execute("SET FOREIGN_KEY_CHECKS=1"); }
            } catch (java.sql.SQLException e) { throw new IllegalStateException("Synthetic schema import failed", e); }
        }
        TestPropertyValues.of(
                "spring.datasource.url=" + IsolatedMysql.url(schema),
                "spring.datasource.username=" + IsolatedMysql.username(),
                "spring.datasource.password=" + IsolatedMysql.password(),
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "spring.jpa.hibernate.ddl-auto=" + (profiles.contains("mysql-schema") ? "validate" : "create-only"),
                "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
                "spring.flyway.enabled=false",
                "app.tasks.reminders.enabled=false",
                "app.upload.dir=./target/test-uploads/mysql/" + schema
        ).applyTo(context.getEnvironment());
    }
}
