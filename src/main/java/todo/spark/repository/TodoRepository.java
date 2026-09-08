package todo.spark.repository;

import todo.spark.model.Todo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface TodoRepository {

    Todo create(String title, String description, Instant dueDate);

    Optional<Todo> findById(long id);

    List<Todo> findAll();

    List<Todo> findByCompleted(boolean completed);

    Optional<Todo> update(long id, String title, String description, Instant dueDate);

    Optional<Todo> toggleCompleted(long id);

    boolean delete(long id);

    int deleteCompleted();

    /**
     * Notified with a human-readable description each time a todo is created, updated,
     * completed/reactivated, or deleted. Decoupled from any particular notification
     * mechanism (e.g. WebSocket broadcast) so implementations don't need to know about it.
     */
    void setChangeListener(Consumer<String> changeListener);
}
