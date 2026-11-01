package com.factory.productmanager.task;

import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
class StageSkipMigrationMysqlTest {
    @Test void additiveSkipHistoryPreservesExistingPieceAndIsRepeatable() throws Exception {
        try(var c=IsolatedMysql.connect("stage_skip_upgrade_synthetic");var s=c.createStatement()) {
            IsolatedMysql.assertOwned(c,"stage_skip_upgrade_synthetic");
            s.execute("CREATE TABLE pieces(id BINARY(16) NOT NULL PRIMARY KEY, current_stage VARCHAR(32) NOT NULL)");
            s.execute("INSERT INTO pieces VALUES(UNHEX(REPEAT('01',16)),'ROUGH_MACHINING')");
            try(var input=getClass().getResourceAsStream("/db/migration/V39__piece_stage_skip_history.sql")) {
                assertNotNull(input);
                String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines()
                        .filter(line->!line.stripLeading().startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));
                for(int i=0;i<2;i++) for(String command:sql.split(";")) if(!command.isBlank()) s.execute(command);
            }
            try(var rows=s.executeQuery("SELECT current_stage FROM pieces")) {assertTrue(rows.next());assertEquals("ROUGH_MACHINING",rows.getString(1));}
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM piece_stage_skips")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
        }
    }
}
