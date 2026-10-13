package com.lelentandane.synthesis.db;

import com.lelentandane.synthesis.model.Concept;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL for concepts. Every query is parameterised. */
public final class ConceptRepository {

    private final Database database;

    public ConceptRepository(Database database) {
        this.database = database;
    }

    private Connection connection() {
        return database.connection();
    }

    public List<Concept> findAll() {
        String sql = "SELECT id, name, discipline, description FROM concepts ORDER BY name COLLATE NOCASE";
        try (PreparedStatement statement = connection().prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            List<Concept> result = new ArrayList<>();
            while (rows.next()) {
                result.add(map(rows));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load concepts", e);
        }
    }

    public Optional<Concept> findById(long id) {
        return findOne("SELECT id, name, discipline, description FROM concepts WHERE id = ?", id);
    }

    public Optional<Concept> findByName(String name) {
        return findOne("SELECT id, name, discipline, description FROM concepts WHERE name = ? COLLATE NOCASE", name);
    }

    private Optional<Concept> findOne(String sql, Object parameter) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the concept", e);
        }
    }

    public long insert(String name, String discipline, String description) {
        String sql = "INSERT INTO concepts (name, discipline, description) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, discipline);
            statement.setString(3, description);
            statement.executeUpdate();
            return lastInsertId();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the concept", e);
        }
    }

    public boolean update(long id, String name, String discipline, String description) {
        String sql = "UPDATE concepts SET name = ?, discipline = ?, description = ? WHERE id = ?";
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, discipline);
            statement.setString(3, description);
            statement.setLong(4, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the concept", e);
        }
    }

    public boolean delete(long id) {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM concepts WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the concept", e);
        }
    }

    public void deleteAll() {
        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("DELETE FROM concepts");
        } catch (SQLException e) {
            throw new DataAccessException("Could not clear concepts", e);
        }
    }

    public int count() {
        try (Statement statement = connection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM concepts")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Could not count concepts", e);
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

    private static Concept map(ResultSet rows) throws SQLException {
        return new Concept(rows.getLong("id"), rows.getString("name"),
                rows.getString("discipline"), rows.getString("description"));
    }
}
