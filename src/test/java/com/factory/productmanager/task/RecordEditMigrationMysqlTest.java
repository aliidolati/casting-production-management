package com.factory.productmanager.task;
import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
class RecordEditMigrationMysqlTest {
 @Test void additiveHistoryMigrationIsRepeatableAndPreservesExistingRecords()throws Exception{
  try(var c=IsolatedMysql.connect("record_edits_upgrade_synthetic");var s=c.createStatement()){
   IsolatedMysql.assertOwned(c,"record_edits_upgrade_synthetic");
   s.execute("CREATE TABLE piece_notes(id INT PRIMARY KEY,content TEXT)");s.execute("INSERT INTO piece_notes VALUES(1,'synthetic note')");
   String sql;try(var in=getClass().getResourceAsStream("/db/migration/V40__record_edit_history.sql")){assertNotNull(in);sql=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
   s.execute(sql);s.execute("INSERT INTO record_edit_events VALUES(UNHEX(REPEAT('01',16)),'notes',UNHEX(REPEAT('02',16)),UNHEX(REPEAT('03',16)),'synthetic',NOW(),'before','after')");s.execute(sql);
   try(var r=s.executeQuery("SELECT content FROM piece_notes")){assertTrue(r.next());assertEquals("synthetic note",r.getString(1));}
   try(var r=s.executeQuery("SELECT COUNT(*) FROM record_edit_events")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
  }
 }
}
