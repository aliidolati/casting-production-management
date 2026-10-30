package com.factory.productmanager.support;

import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Explicit local fallback when Docker is unavailable. Starts its OWN mysqld, never a system service. */
final class NativeIsolatedMysql {
    private NativeIsolatedMysql() { }
    static IsolatedMysql.ServerAddress start() {
        Process server = null;
        try {
            String binary = System.getProperty("mysql.native.bin");
            if (binary == null) throw new IllegalArgumentException("Set mysql.native.bin to a MySQL 8.0 mysqld executable");
            Path executable = Path.of(binary).toAbsolutePath();
            Path base = executable.getParent().getParent();
            Path target = Path.of("target").toAbsolutePath().normalize();
            Files.createDirectories(target);
            Path data = Files.createTempDirectory(target, "mysql-cloud-synthetic-");
            Path initLog = target.resolve(data.getFileName() + "-initialize.log");
            var initialize = new ProcessBuilder(executable.toString(), "--no-defaults", "--basedir=" + base,
                    "--datadir=" + data, "--initialize-insecure").redirectErrorStream(true).redirectOutput(initLog.toFile()).start();
            if (!initialize.waitFor(60, TimeUnit.SECONDS)) {
                initialize.destroyForcibly(); throw new IllegalStateException("Owned MySQL initialization timed out");
            }
            if (initialize.exitValue() != 0) throw new IllegalStateException("Owned MySQL initialization failed; see ignored target log");
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            var serverArgs = new ArrayList<>(List.of(executable.toString(), "--no-defaults", "--basedir=" + base,
                    "--datadir=" + data, "--bind-address=127.0.0.1", "--port=" + port, "--mysqlx=OFF",
                    "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--secure-file-priv=" + data, "--skip-log-bin"));
            if (System.getProperty("os.name").startsWith("Windows")) serverArgs.add("--console");
            server = new ProcessBuilder(serverArgs)
                    .redirectErrorStream(true).redirectOutput(data.resolve("server.log").toFile()).start();
            String url = "jdbc:mysql://127.0.0.1:" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
            Connection connection = null;
            Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
            while (Instant.now().isBefore(deadline) && server.isAlive()) {
                try { connection = DriverManager.getConnection(url, "root", ""); break; }
                catch (SQLException e) { Thread.sleep(250); }
            }
            if (connection == null) throw new IllegalStateException("Owned MySQL did not become ready");
            String password = UUID.randomUUID().toString();
            try (var c = connection; var s = c.createStatement()) {
                if (!c.getMetaData().getDatabaseProductVersion().startsWith("8.0.")) throw new IllegalStateException("Use MySQL 8.0, not MariaDB or another major");
                s.execute("CREATE DATABASE bootstrap_synthetic CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                s.execute("CREATE USER 'synthetic'@'%' IDENTIFIED BY '" + password + "'");
                s.execute("GRANT ALL PRIVILEGES ON bootstrap_synthetic.* TO 'synthetic'@'%'");
                s.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "'");
            }
            Process ownedServer = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Graceful SQL shutdown also stops Windows servers that fork a child process.
                try (var c = DriverManager.getConnection(url + "&connectTimeout=2000&socketTimeout=3000", "root", password);
                     var s = c.createStatement()) { s.execute("SHUTDOWN"); }
                catch (SQLException ignored) { /* Already stopped, or fall back to owned process handles. */ }
                var children = ownedServer.descendants().toList();
                children.forEach(ProcessHandle::destroy);
                try { if (!ownedServer.waitFor(10, TimeUnit.SECONDS)) ownedServer.destroyForcibly(); }
                catch (InterruptedException e) { ownedServer.destroyForcibly(); Thread.currentThread().interrupt(); }
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            }, "stop-owned-synthetic-mysql"));
            return new IsolatedMysql.ServerAddress(url.replace("/?", "/bootstrap_synthetic?"), "synthetic", password);
        } catch (Exception e) {
            if (server != null) server.destroyForcibly();
            throw new IllegalStateException("Could not start owned native synthetic MySQL", e);
        }
    }
}
