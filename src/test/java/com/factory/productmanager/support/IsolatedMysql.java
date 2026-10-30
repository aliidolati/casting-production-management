package com.factory.productmanager.support;

import org.testcontainers.mysql.MySQLContainer;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Test-only server owned by this JVM. Accepts no external URL or production credential. */
public final class IsolatedMysql {
    private IsolatedMysql() { }
    private static final Set<String> SCHEMAS = new HashSet<>();
    record ServerAddress(String url, String username, String password) { }
    private static class Server {
        static final ServerAddress MYSQL = start();
        private static ServerAddress start() {
            if (!Boolean.getBoolean("mysql.tests.enabled")) {
                throw new IllegalStateException("Use -Pmysql-it verify; an isolated Docker runtime is required.");
            }
            if ("native".equals(System.getProperty("mysql.tests.runtime"))) return NativeIsolatedMysql.start();
            var mysql = new MySQLContainer("mysql:8.0.31")
                    .withDatabaseName("bootstrap_synthetic")
                    .withUsername("synthetic")
                    .withPassword(UUID.randomUUID().toString())
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
                    .withReuse(false);
            mysql.start(); // Missing Docker/image startup fails the job, never silently skips.
            return new ServerAddress(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }
    public static synchronized String url(String schema) {
        if (!schema.matches("[a-z_]+_synthetic")) throw new IllegalArgumentException("Synthetic schema name required");
        var mysql = Server.MYSQL;
        String url = mysql.url().replace("/bootstrap_synthetic", "/" + schema);
        if (SCHEMAS.add(schema)) {
            try (var c = DriverManager.getConnection(mysql.url(), "root", password()); var s = c.createStatement()) {
                s.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                s.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO 'synthetic'@'%'");
            } catch (SQLException e) {
                SCHEMAS.remove(schema);
                throw new IllegalStateException("Could not create owned synthetic schema", e);
            }
        }
        return url;
    }
    public static String username() { return Server.MYSQL.username(); }
    public static String password() { return Server.MYSQL.password(); }
    public static Connection connect(String schema) throws SQLException {
        return DriverManager.getConnection(url(schema), username(), password());
    }
    public static void assertOwned(Connection c, String schema) throws SQLException {
        if (!schema.equals(c.getCatalog()) || !url(schema).equals(c.getMetaData().getURL())) {
            throw new IllegalStateException("Datasource is not the owned synthetic database");
        }
    }
}
