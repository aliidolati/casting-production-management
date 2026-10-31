package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.PieceStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="scrap.browser.enabled",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.address=127.0.0.1",
    "spring.datasource.url=jdbc:h2:mem:scrap_browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "app.upload.dir=./target/test-uploads/scrap-browser"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ScrapBrowserSmokeTest extends TaskTestBase {
    @LocalServerPort int port;
    @Test void createEditCancelArchiveReportAndCsvOnDesktopAndMobile() throws Exception {
        var p=newPiece("SCRUI"+UUID.randomUUID().toString().substring(0,6),"Synthetic scrap UI",1);
        var legacy=new com.factory.productmanager.model.entity.Piece();
        legacy.setPieceDefinition(p.getPieceDefinition());legacy.setPieceNumber(2);
        legacy.setCurrentStage(PieceStage.SCRAPPED);legacy.setArchivedAt(LocalDateTime.now());pieceRepository.saveAndFlush(legacy);
        var builder=new ProcessBuilder("node","src/test/js/scrap.browser.cjs");
        var env=builder.environment();
        env.put("SCRAP_BASE","http://127.0.0.1:"+port);env.put("SCRAP_ADMIN",admin.getUsername());
        env.put("SCRAP_PASSWORD",PASSWORD);env.put("SCRAP_PIECE",p.getId().toString());
        env.put("SCRAP_PROJECT",p.getPieceDefinition().getProject().getId().toString());
        var output=Path.of("target/scrap-browser.log");
        var process=builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(120,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Scrap browser timed out"); }
        assertEquals(0,process.exitValue(),Files.readString(output));
    }
}
