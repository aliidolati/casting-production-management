package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.service.MeltPassportService;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a fresh Testcontainers-owned synthetic MySQL schema. */
@EnabledIfSystemProperty(named="melt.mysql.enabled",matches="true")
@org.springframework.test.context.ContextConfiguration(initializers=com.factory.productmanager.support.MysqlTestContextInitializer.class)
@ActiveProfiles("melt-mysql")
@SpringBootTest(properties="spring.jpa.properties.hibernate.session_factory.statement_inspector=com.factory.productmanager.task.MeltCandidateMysqlIntegrationTest$SqlCapture")
class MeltCandidateMysqlIntegrationTest extends TaskTestBase {
    @Autowired MeltPassportService service;
    @Autowired JdbcTemplate jdbc;

    public static class SqlCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();
        @Override public String inspect(String sql) { SQL.add(sql);return sql; }
    }

    @Test void clockMigrationIsCompatibleWithHibernateCreatedSchema() throws Exception {
        try (var connection=jdbc.getDataSource().getConnection()) {
            com.factory.productmanager.support.IsolatedMysql.assertOwned(connection,"melt_lookup_synthetic");
            try (var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,"melt_passports","time_at610_clock")) {
                assertTrue(columns.next());assertEquals(java.sql.Types.TIME,columns.getInt("DATA_TYPE"));
            }
            MeltClockMysqlIntegrationTest.applyMigration(connection);
            MeltClockMysqlIntegrationTest.applyMigration(connection);
        }
    }

    @Test void lookupWorksWithMixedColumnCollationsAndDoesNotCastNumericReferences() {
        String url=jdbc.execute((java.sql.Connection connection)->connection.getMetaData().getURL());
        assertEquals(com.factory.productmanager.support.IsolatedMysql.url("melt_lookup_synthetic"),url);
        // Only changes tables inside the disposable database configured by the test profile.
        jdbc.execute("alter table projects modify code varchar(255) character set utf8mb4 collate utf8mb4_unicode_ci not null");
        jdbc.execute("alter table piece_definitions modify piece_name varchar(255) character set utf8mb4 collate utf8mb4_0900_ai_ci not null");
        String code="MYSQL-MELT-"+UUID.randomUUID().toString().substring(0,8);
        var piece=newPiece(code,"قطعه آزمون کوره",27);piece.setCurrentStage(PieceStage.CASTING);pieceRepository.saveAndFlush(piece);
        var legacyError=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.queryForList("""
                select p.id from pieces p join piece_definitions d on d.id=p.piece_definition_id
                join projects project on project.id=d.project_id
                where lower(concat(project.code,'-',cast(d.row_num as char),'-',cast(p.piece_number as char)))
                    like lower(concat('%',?,'%'))
                """,code));
        var sqlError=assertInstanceOf(java.sql.SQLException.class,legacyError.getMostSpecificCause());
        assertEquals(1267,sqlError.getErrorCode());
        SqlCapture.SQL.clear();
        for(String query:List.of(code+"-1-27",code+"-۱-۲۷","۲۷","قطعه آزمون",code,"")) {
            assertTrue(service.candidates(query,worker.getUsername()).stream().anyMatch(p->p.id().equals(piece.getId())),query);
        }
        var candidateSql=SqlCapture.SQL.stream().filter(s->s.contains("not exists")&&s.contains("melt_passport_pieces")).toList();
        assertEquals(6,candidateSql.size());
        assertTrue(candidateSql.stream().noneMatch(s->s.toLowerCase().contains("cast(")||s.toLowerCase().contains("concat(")));
        assertTrue(service.candidates("%",worker.getUsername()).isEmpty());
    }
}
