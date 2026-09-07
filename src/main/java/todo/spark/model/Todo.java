package todo.spark.model;

import java.time.Instant;
import java.time.ZoneOffset;

public record Todo(long id, String title, String description, boolean completed, Instant createdAt, Instant dueDate) {

    public Todo(long id, String title, String description, Instant dueDate) {
        this(id, title, description, false, Instant.now(), dueDate);
    }

    /**
     * yyyy-MM-dd, suitable for display and for an HTML {@code <input type="date">} value.
     * Empty string when there is no due date, since FreeMarker templates have no reliable
     * way to format a raw {@link Instant} themselves.
     * <p>Named with a "get" prefix (unlike the record components above) because FreeMarker's
     * bean-style property resolution needs it, whereas the components themselves are exposed
     * automatically by FreeMarker's record support.
     */
    public String getFormattedDueDate() {
        return dueDate == null ? "" : dueDate.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
}
