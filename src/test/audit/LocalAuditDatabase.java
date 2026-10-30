import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in helper for the user's explicitly authorized LOCAL test database audit.
 * Does not start Spring, run migrations, reset schemas, or read business row contents.
 * Credentials stay in memory. Cleanup is limited to this run's newly created roots.
 */
class LocalAuditDatabase {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static byte[] uuid(String s) {
        var u = UUID.fromString(s);
        return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }
    static String q(String s) {
        if (!s.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid SQL identifier");
        return "`" + s + "`";
    }
    static List<Map<String,Object>> rows(Connection c, String sql, Object... args) throws Exception {
        try(var s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try(var r=s.executeQuery()) {
                var out=new ArrayList<Map<String,Object>>();
                while(r.next()) { var row=new LinkedHashMap<String,Object>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }
    record ForeignKey(String table,String column,String parent,String parentColumn) {}
    static final Map<String,Integer> deleted=new TreeMap<>();
    static final Set<Path> syntheticFiles=new HashSet<>();
    static void deleteWhere(Connection c, List<ForeignKey> keys, String table, String column, Object value, Path uploadRoot) throws Exception {
        for(var row:rows(c,"select * from "+q(table)+" where "+q(column)+"=?",value)) {
            for(var fk:keys) if(fk.parent.equals(table) && row.get(fk.parentColumn)!=null)
                deleteWhere(c,keys,fk.table,fk.column,row.get(fk.parentColumn),uploadRoot);
            for(var entry:row.entrySet()) if(entry.getKey().contains("path")&&entry.getValue() instanceof String s&&!s.isBlank()) {
                Path file=uploadRoot.resolve(s).normalize();
                if(!file.startsWith(uploadRoot)) throw new IllegalStateException("Upload path escapes configured storage");
                if(s.startsWith("drawings/")||s.startsWith("signatures/")) syntheticFiles.add(file);
            }
        }
        try(var stmt=c.prepareStatement("delete from "+q(table)+" where "+q(column)+"=?")) {
            stmt.setObject(1,value); int n=stmt.executeUpdate();if(n>0) deleted.merge(table,n,Integer::sum);
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2 || !args[0].equals("--authorized-local-test-db"))
            throw new IllegalArgumentException("Explicit local test database authorization required");
        Properties p=new Properties();
        try(var in=Files.newBufferedReader(Path.of("src/main/resources/application.properties"),StandardCharsets.UTF_8)){p.load(in);}
        String url=p.getProperty("spring.datasource.url");
        if(!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):3306/[^?]+.*"))
            throw new IllegalStateException("This helper only permits the configured loopback MySQL on port 3306");
        try(var c=DriverManager.getConnection(url,p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"))) {
            c.setAutoCommit(false);
            switch(args[1]) {
                case "inspect" -> {
                    var out=new LinkedHashMap<String,Object>();
                    out.put("serverVersion",c.getMetaData().getDatabaseProductVersion());
                    var tables=rows(c,"select table_name,engine,table_collation from information_schema.tables where table_schema=database()");
                    out.put("tables",tables);
                    out.put("columns",rows(c,"select table_name,column_name,column_type,is_nullable,column_key from information_schema.columns where table_schema=database()"));
                    out.put("foreignKeys",rows(c,"select table_name,column_name,referenced_table_name,referenced_column_name from information_schema.key_column_usage where table_schema=database() and referenced_table_name is not null"));
                    out.put("grants",rows(c,"SHOW GRANTS").stream().map(row->row.values().iterator().next().toString().replaceAll("TO .*", "TO <current principal>")).toList());
                    var counts=new TreeMap<String,Object>();
                    for(var t:tables) {
                        String table=t.get("TABLE_NAME").toString();
                        counts.put(table,rows(c,"select count(*) as n from "+q(table)).get(0).get("n"));
                    }
                    out.put("rowCounts",counts);
                    out.put("auditRootsRemaining",Map.of(
                        "users",rows(c,"select count(*) as n from users where username regexp '^AUDIT_20261030_[a-f0-9]{8}_'").get(0).get("n"),
                        "projects",rows(c,"select count(*) as n from projects where code regexp '^AUDIT_20261030_[a-f0-9]{8}($|_)'").get(0).get("n"),
                        "materials",rows(c,"select count(*) as n from melt_materials where name regexp '^AUDIT_20261030_[a-f0-9]{8}_'").get(0).get("n"),
                        "batches",rows(c,"select count(*) as n from heat_treatment_batches where batch_code regexp '^AUDIT_20261030_[a-f0-9]{8}_'").get(0).get("n")));
                    out.put("flywayHistoryPresent",counts.containsKey("flyway_schema_history"));
                    if(counts.containsKey("flyway_schema_history")) out.put("migrationHistory",rows(c,"select version,type,success from flyway_schema_history order by installed_rank"));
                    out.put("bootstrapCredentialStillActive",false);
                    // Only compare hashes in memory; never output a username, password or hash.
                    String seeder=Files.readString(Path.of("src/main/java/com/factory/productmanager/config/DataSeeder.java"));
                    var un=java.util.regex.Pattern.compile("DEFAULT_USERNAME\\s*=\\s*\"([^\"]+)\"").matcher(seeder);
                    var pw=java.util.regex.Pattern.compile("DEFAULT_PASSWORD\\s*=\\s*\"([^\"]+)\"").matcher(seeder);
                    if(un.find()&&pw.find()) for(var r:rows(c,"select password from users where username=?",un.group(1)))
                        out.put("bootstrapCredentialStillActive",new BCryptPasswordEncoder().matches(pw.group(1),r.get("password").toString()));
                    Files.createDirectories(Path.of("target/audit"));
                    Files.writeString(Path.of("target/audit/database-metadata.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out));
                    System.out.println(JSON.writeValueAsString(Map.of("connected",true,"tableCount",counts.size(),"flywayHistoryPresent",out.get("flywayHistoryPresent"),"bootstrapCredentialStillActive",out.get("bootstrapCredentialStillActive"),"serverVersion",out.get("serverVersion"))));
                    c.rollback();
                }
                case "seed" -> {
                    Map input=JSON.readValue(System.in,Map.class);
                    String name=input.get("username").toString();
                    if(!name.matches("AUDIT_20261030_[a-z0-9]+_admin")) throw new IllegalArgumentException("Audit namespace required");
                    String id=UUID.randomUUID().toString();
                    try(var s=c.prepareStatement("insert into users(id,username,password,full_name,role,disabled,created_at) values(?,?,?,?,?,false,now())")) {
                        s.setBytes(1,uuid(id));s.setString(2,name);s.setString(3,new BCryptPasswordEncoder().encode(input.get("password").toString()));
                        s.setString(4,"کاربر موقت بررسی سامانه");s.setString(5,"ADMIN");s.executeUpdate();
                    }
                    c.commit(); System.out.println(JSON.writeValueAsString(Map.of("id",id,"username",name)));
                }
                case "cleanup" -> {
                    Map input=JSON.readValue(System.in,Map.class);
                    String run=input.get("run").toString();
                    if(!run.matches("AUDIT_20261030_[a-z0-9]+")) throw new IllegalArgumentException("Audit namespace required");
                    Path uploadRoot=Path.of(p.getProperty("app.upload.dir","./uploads")).toAbsolutePath().normalize();
                    var keys=new ArrayList<ForeignKey>();
                    for(var k:rows(c,"select table_name,column_name,referenced_table_name,referenced_column_name from information_schema.key_column_usage where table_schema=database() and referenced_table_name is not null"))
                        keys.add(new ForeignKey(k.get("TABLE_NAME").toString(),k.get("COLUMN_NAME").toString(),k.get("REFERENCED_TABLE_NAME").toString(),k.get("REFERENCED_COLUMN_NAME").toString()));
                    String prefix=run+"_";
                    var auditProjects=rows(c,"select id from projects where code=? or left(code,?)=?",run,prefix.length(),prefix);
                    for(var project:auditProjects) {
                        // Passport is the parent of membership links, so find it before deleting pieces.
                        for(var passport:rows(c,"select distinct m.passport_id as id from melt_passport_pieces m join pieces p on p.id=m.piece_id join piece_definitions d on d.id=p.piece_definition_id where d.project_id=?",project.get("id")))
                            deleteWhere(c,keys,"melt_passports","id",passport.get("id"),uploadRoot);
                        deleteWhere(c,keys,"projects","id",project.get("id"),uploadRoot);
                    }
                    for(var user:rows(c,"select id from users where left(username,?)=?",prefix.length(),prefix))
                        deleteWhere(c,keys,"users","id",user.get("id"),uploadRoot);
                    for(var material:rows(c,"select id from melt_materials where left(name,?)=?",prefix.length(),prefix))
                        deleteWhere(c,keys,"melt_materials","id",material.get("id"),uploadRoot);
                    for(var batch:rows(c,"select id from heat_treatment_batches where left(batch_code,?)=?",prefix.length(),prefix))
                        deleteWhere(c,keys,"heat_treatment_batches","id",batch.get("id"),uploadRoot);
                    c.commit();
                    int removedFiles=0;
                    for(Path file:syntheticFiles) if(Files.deleteIfExists(file)) removedFiles++;
                    var result=Map.of("run",run,"deletedRows",deleted,"deletedFiles",removedFiles);
                    Files.writeString(Path.of("target/audit/cleanup-"+run+".json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                    System.out.println(JSON.writeValueAsString(result));
                }
                default -> throw new IllegalArgumentException("Unknown operation");
            }
        } catch(Exception e) {
            // No connection URLs, property values, raw SQL errors or credentials in output.
            System.err.println("Local audit database operation failed: "+e.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
