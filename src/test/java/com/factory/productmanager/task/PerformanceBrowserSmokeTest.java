package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real HTTP/browser smoke, using only the test H2 datasource and synthetic users. */
@EnabledIfSystemProperty(named = "performance.browser.enabled", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:performance_browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "app.upload.dir=./target/test-uploads/performance-browser"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerformanceBrowserSmokeTest extends TaskTestBase {
    @LocalServerPort int port;
    @Test void profileEvaluationInDesktopAndMobile() throws Exception {
        var grader = newUser("browser-grader-" + UUID.randomUUID(), Role.ADMIN,
                Set.of(Permission.VIEW_USERS, Permission.EDIT_PERFORMANCE_SCORES));
        var builder = new ProcessBuilder("node", "src/test/js/performance.browser.cjs");
        var env = builder.environment();
        env.putIfAbsent("NODE_PATH", "C:/Users/Asus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules");
        env.put("PERFORMANCE_BASE", "http://127.0.0.1:" + port);
        env.put("PERFORMANCE_ADMIN", admin.getUsername()); env.put("PERFORMANCE_WORKER", worker.getUsername());
        env.put("PERFORMANCE_MANAGER", manager.getUsername()); env.put("PERFORMANCE_PASSWORD", PASSWORD);
        env.put("PERFORMANCE_GRADER", grader.getUsername());
        env.put("PERFORMANCE_WORKER_ID", worker.getId().toString()); env.put("PERFORMANCE_OTHER_ID", otherWorker.getId().toString());
        var output = Path.of("target/performance-browser.log");
        var process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(150, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Browser smoke timed out"); }
        assertEquals(0, process.exitValue(), Files.readString(output));
    }
}
