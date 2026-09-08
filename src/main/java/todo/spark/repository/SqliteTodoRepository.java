package todo.spark.repository;

import todo.spark.model.Todo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
            try (Statement statement = connection.createStatement()) {
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
        String sql = "INSERT INTO todos (title, description, completed, created_at, due_date) VALUES (?, ?, 0, ?, ?)";
        Instant createdAt = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, createdAt.toString());
            statement.setString(4, dueDate == null ? null : dueDate.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                notifyChange("Added \"%s\"".formatted(title));
                return new Todo(id, title, description, false, createdAt, dueDate);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create todo", e);
        }
    }

    @Override
    public synchronized Optional<Todo> findById(long id) {
        String sql = "SELECT * FROM todos WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find todo " + id, e);
        }
    }

    @Override
    public synchronized List<Todo> findAll() {
        String sql = "SELECT * FROM todos ORDER BY created_at";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return mapRows(rs);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list todos", e);
        }
    }

    @Override
    public synchronized List<Todo> findByCompleted(boolean completed) {
        String sql = "SELECT * FROM todos WHERE completed = ? ORDER BY created_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, completed ? 1 : 0);
            try (ResultSet rs = statement.executeQuery()) {
                return mapRows(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list todos by completed=" + completed, e);
        }
    }

    @Override
    public synchronized Optional<Todo> update(long id, String title, String description, Instant dueDate) {
        return findById(id).map(existing -> {
            String sql = "UPDATE todos SET title = ?, description = ?, due_date = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, title);
                statement.setString(2, description);
                statement.setString(3, dueDate == null ? null : dueDate.toString());
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
            boolean newCompleted = !existing.completed();
            String sql = "UPDATE todos SET completed = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
            String sql = "DELETE FROM todos WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
        String sql = "DELETE FROM todos WHERE completed = 1";
        try (Statement statement = connection.createStatement()) {
            int count = statement.executeUpdate(sql);
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
        List<Todo> todos = new ArrayList<>();
        while (rs.next()) {
            todos.add(mapRow(rs));
        }
        return todos;
    }

    private static Todo mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String title = rs.getString("title");
        String description = rs.getString("description");
        boolean completed = rs.getInt("completed") != 0;
        Instant createdAt = Instant.parse(rs.getString("created_at"));
        String dueDateString = rs.getString("due_date");
        Instant dueDate = dueDateString == null ? null : Instant.parse(dueDateString);
        return new Todo(id, title, description, completed, createdAt, dueDate);
    }
}
