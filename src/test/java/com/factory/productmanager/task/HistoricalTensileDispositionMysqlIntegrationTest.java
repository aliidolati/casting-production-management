package com.factory.productmanager.task;

import com.factory.productmanager.support.MysqlTestContextInitializer;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
@ActiveProfiles("mysql-cloud")
@ContextConfiguration(initializers=MysqlTestContextInitializer.class)
class HistoricalTensileDispositionMysqlIntegrationTest extends HistoricalTensileDispositionIntegrationTest { }
