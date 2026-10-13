package com.lelentandane.synthesis.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection. All access goes through GraphService, which is
 * synchronized, so one connection is enough and keeps transactions simple.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Connection connection;

    private Database(String url) {
        try {
            connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            applySchema();
        } catch (SQLException e) {
            throw new DataAccessException("Could not open the database", e);
        }
    }

    /** Opens (and creates if needed) a database file, including its parent folder. */
    public static Database open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new DataAccessException("Could not create the data folder", e);
        }
        return new Database("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** A throwaway database that disappears when closed. Used by tests. */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    public Connection connection() {
        return connection;
    }

    /** Runs the work in one transaction: everything is saved, or nothing is. */
    public <T> T transaction(SqlWork<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database transaction failed", e);
        }
    }

    private void applySchema() {
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new DataAccessException("schema.sql is missing from the application", null);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String part : sql.split(";")) {
                    String cleaned = stripComments(part).trim();
                    if (!cleaned.isEmpty()) {
                        statement.execute(cleaned);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            throw new DataAccessException("Could not create the database tables", e);
        }
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.trim().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Could not close the database", e);
        }
    }
}
