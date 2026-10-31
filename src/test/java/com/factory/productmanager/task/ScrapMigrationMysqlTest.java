package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
class ScrapMigrationMysqlTest {
    @Test void additiveMigrationIsRepeatablePreservesLegacyRowsAndEnforcesOneActiveCycle() throws Exception {
        try (var c=IsolatedMysql.connect("scrap_migration_synthetic");var s=c.createStatement()) {
            IsolatedMysql.assertOwned(c,"scrap_migration_synthetic");
            s.execute("CREATE TABLE pieces(id BINARY(16) PRIMARY KEY,current_stage VARCHAR(32) NOT NULL)");
            s.execute("CREATE TABLE radiography_defects(id BINARY(16) PRIMARY KEY)");
            s.execute("INSERT INTO pieces VALUES(UNHEX(REPEAT('01',16)),'SCRAPPED')");
            var migration=new ResourceDatabasePopulator(new ClassPathResource("db/migration/V37__piece_scrap_dispatches.sql"));
            migration.populate(c); migration.populate(c);
            try(var rows=s.executeQuery("SELECT current_stage FROM pieces")) { assertTrue(rows.next());assertEquals("SCRAPPED",rows.getString(1)); }
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM piece_scrap_dispatches")) { assertTrue(rows.next());assertEquals(0,rows.getInt(1)); }
            String fields="(id,piece_id,active_piece_key,defect_type,updated_at,sent_at)";
            String values="UNHEX(REPEAT('01',16)),UNHEX(REPEAT('01',16)),'ابعادی',NOW(6),'2026-10-01 12:00:00'";
            s.execute("INSERT INTO piece_scrap_dispatches "+fields+" VALUES(UNHEX(REPEAT('02',16)),"+values+")");
            assertThrows(SQLException.class,()->s.execute("INSERT INTO piece_scrap_dispatches "+fields+" VALUES(UNHEX(REPEAT('03',16)),"+values+")"));
            assertThrows(SQLException.class,()->s.execute("INSERT INTO piece_scrap_dispatch_defects VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('99',16)))"));
            s.execute("UPDATE piece_scrap_dispatches SET active_piece_key=NULL,reversed_at=NOW(6)");
            s.execute("INSERT INTO piece_scrap_dispatches "+fields+" VALUES(UNHEX(REPEAT('03',16)),"+values+")");
            s.execute("UPDATE piece_scrap_dispatches SET defect_type='OTHER',defect_type_custom='علت ساختگی' WHERE active_piece_key IS NOT NULL");
            try(var rows=s.executeQuery("SELECT COUNT(*),MIN(sent_at),MAX(notes) FROM piece_scrap_dispatches")) {
                assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals("2026-10-01 12:00:00",rows.getString(2));assertNull(rows.getString(3));
            }
        }
    }
}
