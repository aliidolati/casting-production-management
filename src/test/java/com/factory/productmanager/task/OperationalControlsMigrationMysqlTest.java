package com.factory.productmanager.task;
import com.factory.productmanager.support.IsolatedMysql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="mysql.tests.enabled",matches="true")
class OperationalControlsMigrationMysqlTest {
 @Test void additiveUpgradePreservesLegacyRowsAndRepeatsWithoutInventingSnapshots()throws Exception{
  try(var c=IsolatedMysql.connect("operational_upgrade_synthetic");var s=c.createStatement()){
   IsolatedMysql.assertOwned(c,"operational_upgrade_synthetic");
   s.execute("CREATE TABLE heat_treatment_batches(id INT PRIMARY KEY, batch_code VARCHAR(50))");s.execute("INSERT INTO heat_treatment_batches VALUES(1,'synthetic')");
   s.execute("CREATE TABLE piece_welding_dispatches(id INT PRIMARY KEY,notes TEXT)");s.execute("INSERT INTO piece_welding_dispatches VALUES(1,'legacy synthetic')");
   s.execute("CREATE TABLE melt_passports(id INT PRIMARY KEY,status ENUM('DRAFT','PENDING_APPROVAL','NEEDS_CORRECTION','APPROVED') NOT NULL)");s.execute("INSERT INTO melt_passports VALUES(1,'APPROVED')");
   String sql;try(var in=getClass().getResourceAsStream("/db/migration/V41__operational_cancellation_and_batch_audit.sql")){assertNotNull(in);sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().filter(l->!l.startsWith("--")).collect(java.util.stream.Collectors.joining("\n"));}
   for(int i=0;i<2;i++)for(String command:sql.split(";"))if(!command.isBlank())s.execute(command);
   try(var r=s.executeQuery("SELECT notes,dispatch_piece_version,cancelled_at FROM piece_welding_dispatches")){assertTrue(r.next());assertEquals("legacy synthetic",r.getString(1));assertNull(r.getObject(2));assertNull(r.getObject(3));}
   try(var r=s.executeQuery("SELECT version FROM heat_treatment_batches")){assertTrue(r.next());assertEquals(0,r.getLong(1));}
   try(var r=s.executeQuery("SELECT status FROM melt_passports")){assertTrue(r.next());assertEquals("APPROVED",r.getString(1));}
   s.execute("INSERT INTO melt_passports VALUES(2,'CANCELLED')");
  }
 }
}
