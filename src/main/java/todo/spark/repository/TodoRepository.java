package todo.spark.repository;

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
 */
public class TodoRepository {

    private final Map<Long, Todo> todosById = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public Todo create(String title, String description, Instant dueDate) {
        long id = nextId.getAndIncrement();
        Todo todo = new Todo(id, title, description, dueDate);
        todosById.put(id, todo);
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
            return todo;
        });
    }

    public Optional<Todo> toggleCompleted(long id) {
        return findById(id).map(todo -> {
            todo.setCompleted(!todo.isCompleted());
            return todo;
        });
    }

    public boolean delete(long id) {
        return todosById.remove(id) != null;
    }

    public int deleteCompleted() {
        List<Long> completedIds = findByCompleted(true).stream().map(Todo::getId).toList();
        completedIds.forEach(todosById::remove);
        return completedIds.size();
    }
}
