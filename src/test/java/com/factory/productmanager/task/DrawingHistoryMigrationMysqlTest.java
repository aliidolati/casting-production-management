package com.factory.productmanager.task;
import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
class DrawingHistoryMigrationMysqlTest {
 @Test void upgradePreservesLegacyPathsAndDoesNotInventMarkVersionOrUploader()throws Exception{
  try(var c=IsolatedMysql.connect("drawing_history_upgrade_synthetic");var s=c.createStatement()){
   IsolatedMysql.assertOwned(c,"drawing_history_upgrade_synthetic");
   s.execute("CREATE TABLE piece_definitions(id BINARY(16) PRIMARY KEY,drawing_image_path VARCHAR(255))");
   s.execute("INSERT INTO piece_definitions VALUES(UNHEX(REPEAT('01',16)),'drawings/legacy.png')");
   s.execute("CREATE TABLE welding_map_marks(id INT PRIMARY KEY,x_percent DOUBLE)");s.execute("INSERT INTO welding_map_marks VALUES(1,0.25)");
   String sql;try(var in=getClass().getResourceAsStream("/db/migration/V42__drawing_revision_history.sql")){assertNotNull(in);sql=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
   for(int i=0;i<2;i++)for(String command:sql.split(";"))if(!command.isBlank())s.execute(command);
   try(var r=s.executeQuery("SELECT drawing_image_path FROM piece_definitions")){assertTrue(r.next());assertEquals("drawings/legacy.png",r.getString(1));}
   try(var r=s.executeQuery("SELECT drawing_revision_id,x_percent FROM welding_map_marks")){assertTrue(r.next());assertNull(r.getObject(1));assertEquals(.25,r.getDouble(2));}
   try(var r=s.executeQuery("SELECT COUNT(*) FROM drawing_revisions")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
  }
 }
}
