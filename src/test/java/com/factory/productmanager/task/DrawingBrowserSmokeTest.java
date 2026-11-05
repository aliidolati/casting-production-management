package com.factory.productmanager.task;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfSystemProperty(named = "drawing.browser.enabled", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:drawing_browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.upload.dir=./target/test-uploads/drawing-browser"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DrawingBrowserSmokeTest extends TaskTestBase {
    @LocalServerPort int port;

    @Test void drawingVersionsAndDeletionPreviewInChromium() throws Exception {
        var piece = newPiece("DRAW-UI-" + UUID.randomUUID().toString().substring(0, 8), "Synthetic drawing", 1);
        var definition = piece.getPieceDefinition();
        var builder = new ProcessBuilder("node", "src/test/js/drawing-history.browser.cjs");
        var env = builder.environment();
        env.put("DRAW_BASE", "http://127.0.0.1:" + port);
        env.put("DRAW_USER", admin.getUsername());
        env.put("DRAW_PASSWORD", PASSWORD);
        env.put("DRAW_PIECE", piece.getId().toString());
        env.put("DRAW_DEFINITION", definition.getId().toString());
        env.put("DRAW_PROJECT", definition.getProject().getId().toString());
        var output = Path.of("target/drawing-browser.log");
        var process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Drawing browser test timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(output));
    }
}
