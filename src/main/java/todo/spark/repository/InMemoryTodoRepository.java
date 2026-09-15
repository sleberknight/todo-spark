package todo.spark.repository;

import static java.util.Objects.nonNull;

import todo.spark.model.Todo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory storage for {@link Todo} instances. Not persisted across restarts.
 * Since {@code Todo} is an immutable record, updates replace the map entry with a new
 * instance rather than mutating the stored one in place.
 */
public class InMemoryTodoRepository extends AbstractTodoRepository {

    private final Map<Long, Todo> todosById = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Todo create(String title, String description, Instant dueDate) {
        var id = nextId.getAndIncrement();
        var todo = new Todo(id, title, description, dueDate);
        todosById.put(id, todo);
        notifyChange("Added \"%s\"".formatted(title));
        return todo;
    }

    @Override
    public Optional<Todo> findById(long id) {
        return Optional.ofNullable(todosById.get(id));
    }

    @Override
    public List<Todo> findAll() {
        return todosById.values().stream()
                .sorted(Comparator.comparing(Todo::createdAt))
                .toList();
    }

    @Override
    public List<Todo> findByCompleted(boolean completed) {
        return findAll().stream()
                .filter(todo -> todo.completed() == completed)
                .toList();
    }

    @Override
    public Optional<Todo> update(long id, String title, String description, Instant dueDate) {
        return findById(id).map(todo -> {
            var updated = new Todo(todo.id(), title, description, todo.completed(), todo.createdAt(), dueDate);
            todosById.put(id, updated);
            notifyChange("Updated \"%s\"".formatted(title));
            return updated;
        });
    }

    @Override
    public Optional<Todo> toggleCompleted(long id) {
        return findById(id).map(todo -> {
            var toggled = new Todo(todo.id(), todo.title(), todo.description(), !todo.completed(), todo.createdAt(), todo.dueDate());
            todosById.put(id, toggled);
            notifyChange("%s \"%s\"".formatted(toggled.completed() ? "Completed" : "Reactivated", toggled.title()));
            return toggled;
        });
    }

    @Override
    public boolean delete(long id) {
        var removed = todosById.remove(id);
        if (nonNull(removed)) {
            notifyChange("Deleted \"%s\"".formatted(removed.title()));
            return true;
        }
        return false;
    }

    @Override
    public int deleteCompleted() {
        var completedIds = findByCompleted(true).stream().map(Todo::id).toList();
        completedIds.forEach(todosById::remove);
        if (!completedIds.isEmpty()) {
            notifyChange("Cleared %d completed todo%s".formatted(completedIds.size(), completedIds.size() == 1 ? "" : "s"));
        }
        return completedIds.size();
    }
}
