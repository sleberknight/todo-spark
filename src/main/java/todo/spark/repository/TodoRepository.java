package todo.spark.repository;

import todo.spark.model.Todo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory storage for {@link Todo} instances. Not persisted across restarts.
 */
public class TodoRepository {

    private final Map<Long, Todo> todosById = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private Consumer<String> changeListener = message -> { };

    /**
     * Notified with a human-readable description each time a todo is created, updated,
     * completed/reactivated, or deleted. Decoupled from any particular notification
     * mechanism (e.g. WebSocket broadcast) so the repository doesn't need to know about it.
     */
    public void setChangeListener(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    public Todo create(String title, String description, Instant dueDate) {
        long id = nextId.getAndIncrement();
        Todo todo = new Todo(id, title, description, dueDate);
        todosById.put(id, todo);
        changeListener.accept("Added \"%s\"".formatted(title));
        return todo;
    }

    public Optional<Todo> findById(long id) {
        return Optional.ofNullable(todosById.get(id));
    }

    public List<Todo> findAll() {
        return todosById.values().stream()
                .sorted(Comparator.comparing(Todo::getCreatedAt))
                .toList();
    }

    public List<Todo> findByCompleted(boolean completed) {
        return findAll().stream()
                .filter(todo -> todo.isCompleted() == completed)
                .toList();
    }

    public Optional<Todo> update(long id, String title, String description, Instant dueDate) {
        return findById(id).map(todo -> {
            todo.setTitle(title);
            todo.setDescription(description);
            todo.setDueDate(dueDate);
            changeListener.accept("Updated \"%s\"".formatted(title));
            return todo;
        });
    }

    public Optional<Todo> toggleCompleted(long id) {
        return findById(id).map(todo -> {
            todo.setCompleted(!todo.isCompleted());
            changeListener.accept("%s \"%s\"".formatted(todo.isCompleted() ? "Completed" : "Reactivated", todo.getTitle()));
            return todo;
        });
    }

    public boolean delete(long id) {
        Todo removed = todosById.remove(id);
        if (removed != null) {
            changeListener.accept("Deleted \"%s\"".formatted(removed.getTitle()));
            return true;
        }
        return false;
    }

    public int deleteCompleted() {
        List<Long> completedIds = findByCompleted(true).stream().map(Todo::getId).toList();
        completedIds.forEach(todosById::remove);
        if (!completedIds.isEmpty()) {
            changeListener.accept("Cleared %d completed todo%s".formatted(completedIds.size(), completedIds.size() == 1 ? "" : "s"));
        }
        return completedIds.size();
    }
}
