package todo.spark.model;

import java.time.Instant;
import java.util.Objects;

public class Todo {

    private final long id;
    private String title;
    private String description;
    private boolean completed;
    private final Instant createdAt;
    private Instant dueDate;

    public Todo(long id, String title, String description, Instant dueDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = false;
        this.createdAt = Instant.now();
        this.dueDate = dueDate;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Todo other && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Todo{id=%d, title='%s', completed=%s}".formatted(id, title, completed);
    }
}
