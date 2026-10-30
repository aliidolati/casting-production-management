package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="security.browser.enabled",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:security_browser;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.upload.dir=./target/test-uploads/security-browser"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SecurityBrowserSmokeTest extends TaskTestBase {
    @LocalServerPort int port;

    @Test void storedCatalogTextStaysInInputsAndPasswordChangeForcesNewLogin() throws Exception {
        var catalog=newUser("security-catalog-"+UUID.randomUUID(),Role.OPERATOR,
                Set.of(Permission.MANAGE_PROJECTS,Permission.MANAGE_PIECE_DEFINITIONS));
        var builder=new ProcessBuilder("node","src/test/js/security.browser.cjs");
        var env=builder.environment();
        env.put("NODE_PATH",System.getProperty("security.browser.nodePath",
                "C:/Users/Asus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules"));
        env.put("SECURITY_BASE","http://127.0.0.1:"+port);
        env.put("SECURITY_CATALOG",catalog.getUsername());env.put("SECURITY_ADMIN",admin.getUsername());
        env.put("SECURITY_PASSWORD",PASSWORD);
        var output=Path.of("target/security-browser.log");
        var process=builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!process.waitFor(150,TimeUnit.SECONDS)){process.destroyForcibly();fail("Security browser test timed out");}
        assertEquals(0,process.exitValue(),Files.readString(output));
    }
}
