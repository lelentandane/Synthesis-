package com.lelentandane.synthesis.db;

import com.lelentandane.synthesis.model.Relation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL for relations. Every query is parameterised. */
public final class RelationRepository {

    private final Database database;

    public RelationRepository(Database database) {
        this.database = database;
    }

    private Connection connection() {
        return database.connection();
    }

    public List<Relation> findAll() {
        String sql = "SELECT id, from_id, to_id, label, strength FROM relations ORDER BY id";
        try (PreparedStatement statement = connection().prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<Relation> result = new ArrayList<>();
            while (rows.next()) {
                result.add(map(rows));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load relations", e);
        }
    }

    public Optional<Relation> findById(long id) {
        String sql = "SELECT id, from_id, to_id, label, strength FROM relations WHERE id = ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the relation", e);
        }
    }

    public boolean exists(long fromId, long toId, String label) {
        String sql = "SELECT 1 FROM relations WHERE from_id = ? AND to_id = ? AND label = ? COLLATE NOCASE";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, fromId);
            statement.setLong(2, toId);
            statement.setString(3, label);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check the relation", e);
        }
    }

    public long insert(long fromId, long toId, String label, int strength) {
        String sql = "INSERT INTO relations (from_id, to_id, label, strength) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, fromId);
            statement.setLong(2, toId);
            statement.setString(3, label);
            statement.setInt(4, strength);
            statement.executeUpdate();
            return lastInsertId();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the relation", e);
        }
    }

    public boolean delete(long id) {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM relations WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the relation", e);
        }
    }

    public int count() {
        try (Statement statement = connection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM relations")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Could not count relations", e);
        }
    }

    /** The id SQLite just assigned. Works the same on every driver version. */
    private long lastInsertId() throws SQLException {
        try (Statement statement = connection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT last_insert_rowid()")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static Relation map(ResultSet rows) throws SQLException {
        return new Relation(rows.getLong("id"), rows.getLong("from_id"), rows.getLong("to_id"),
                rows.getString("label"), rows.getInt("strength"));
    }
}
