package todo.spark.repository;

import static java.time.Instant.parse;
import static java.util.Objects.isNull;

import todo.spark.model.Todo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed storage for {@link Todo} instances, persisted across restarts.
 * <p>Holds a single long-lived connection rather than opening one per operation: a fresh
 * connection to an in-memory SQLite URL (used in tests) would otherwise get a fresh, empty
 * database every time. Every operation is synchronized on that connection, which is simple
 * and sufficient since SQLite itself is a single-writer database regardless.
 */
public class SqliteTodoRepository extends AbstractTodoRepository implements AutoCloseable {

    private final Connection connection;

    public SqliteTodoRepository(String jdbcUrl) {
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS todos (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            title TEXT NOT NULL,
                            description TEXT,
                            completed INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL,
                            due_date TEXT
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open/initialize SQLite database at " + jdbcUrl, e);
        }
    }

    @Override
    public synchronized Todo create(String title, String description, Instant dueDate) {
        var sql = "INSERT INTO todos (title, description, completed, created_at, due_date) VALUES (?, ?, 0, ?, ?)";
        var createdAt = Instant.now();
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, createdAt.toString());
            statement.setString(4, isNull(dueDate) ? null : dueDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                var id = keys.getLong(1);
                notifyChange("Added \"%s\"".formatted(title));
                return new Todo(id, title, description, false, createdAt, dueDate);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create todo", e);
        }
    }

    @Override
    public synchronized Optional<Todo> findById(long id) {
        var sql = "SELECT id, title, description, completed, created_at, due_date FROM todos WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find todo " + id, e);
        }
    }

    @Override
    public synchronized List<Todo> findAll() {
        var sql = "SELECT id, title, description, completed, created_at, due_date FROM todos ORDER BY created_at";
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            return mapRows(resultSet);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list todos", e);
        }
    }

    @Override
    public synchronized List<Todo> findByCompleted(boolean completed) {
        var sql = "SELECT id, title, description, completed, created_at, due_date FROM todos WHERE completed = ? ORDER BY created_at";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, completed ? 1 : 0);
            try (var resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list todos by completed=" + completed, e);
        }
    }

    @Override
    public synchronized Optional<Todo> update(long id, String title, String description, Instant dueDate) {
        return findById(id).map(existing -> {
            var sql = "UPDATE todos SET title = ?, description = ?, due_date = ? WHERE id = ?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, title);
                statement.setString(2, description);
                statement.setString(3, isNull(dueDate) ? null : dueDate.toString());
                statement.setLong(4, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to update todo " + id, e);
            }
            notifyChange("Updated \"%s\"".formatted(title));
            return new Todo(existing.id(), title, description, existing.completed(), existing.createdAt(), dueDate);
        });
    }

    @Override
    public synchronized Optional<Todo> toggleCompleted(long id) {
        return findById(id).map(existing -> {
            var newCompleted = !existing.completed();
            var sql = "UPDATE todos SET completed = ? WHERE id = ?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, newCompleted ? 1 : 0);
                statement.setLong(2, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to toggle todo " + id, e);
            }
            notifyChange("%s \"%s\"".formatted(newCompleted ? "Completed" : "Reactivated", existing.title()));
            return new Todo(existing.id(), existing.title(), existing.description(), newCompleted, existing.createdAt(), existing.dueDate());
        });
    }

    @Override
    public synchronized boolean delete(long id) {
        return findById(id).map(existing -> {
            var sql = "DELETE FROM todos WHERE id = ?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to delete todo " + id, e);
            }
            notifyChange("Deleted \"%s\"".formatted(existing.title()));
            return true;
        }).orElse(false);
    }

    @Override
    public synchronized int deleteCompleted() {
        var sql = "DELETE FROM todos WHERE completed = 1";
        try (var statement = connection.createStatement()) {
            var count = statement.executeUpdate(sql);
            if (count > 0) {
                notifyChange("Cleared %d completed todo%s".formatted(count, count == 1 ? "" : "s"));
            }
            return count;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear completed todos", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to close database connection", e);
        }
    }

    private static List<Todo> mapRows(ResultSet rs) throws SQLException {
        var todos = new ArrayList<Todo>();
        while (rs.next()) {
            todos.add(mapRow(rs));
        }
        return todos;
    }

    private static Todo mapRow(ResultSet rs) throws SQLException {
        var id = rs.getLong("id");
        var title = rs.getString("title");
        var description = rs.getString("description");
        var completed = rs.getInt("completed") != 0;
        var createdAt = Instant.parse(rs.getString("created_at"));
        var dueDateString = rs.getString("due_date");
        var dueDate = isNull(dueDateString) ? null : parse(dueDateString);
        return new Todo(id, title, description, completed, createdAt, dueDate);
    }
}
