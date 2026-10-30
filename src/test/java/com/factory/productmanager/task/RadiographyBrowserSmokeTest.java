package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/browser smoke on a synthetic H2 database and separate upload directory. */
@EnabledIfSystemProperty(named = "radiography.browser.enabled", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:radiography_browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.upload.dir=./target/test-uploads/radiography-browser"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RadiographyBrowserSmokeTest extends TaskTestBase {
    @LocalServerPort int port;

    @Test void editSaveReopenPrintAndSwitchReviewOnDesktopAndMobile() throws Exception {
        var piece = newPiece("RTUI" + UUID.randomUUID().toString().substring(0, 6), "قطعه ساختگی رادیوگرافی", 1);
        piece.setCurrentStage(PieceStage.RADIOGRAPHY);
        piece.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
        pieceRepository.saveAndFlush(piece);
        var builder = new ProcessBuilder("node", "src/test/js/radiography.browser.cjs");
        var env = builder.environment();
        env.put("NODE_PATH", System.getProperty("radiography.browser.nodePath",
                "C:/Users/Asus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules"));
        env.put("RADIOGRAPHY_BASE", "http://127.0.0.1:" + port);
        env.put("RADIOGRAPHY_ADMIN", admin.getUsername());
        env.put("RADIOGRAPHY_PASSWORD", PASSWORD);
        env.put("RADIOGRAPHY_PIECE", piece.getId().toString());
        var output = Path.of("target/radiography-browser.log");
        var process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(150, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Radiography browser smoke timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(output));
    }
}
