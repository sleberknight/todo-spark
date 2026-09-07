package todo.spark.web;

import java.time.Instant;

/**
 * Request payload shape for both creating and updating a todo.
 */
public record TodoRequest(String title, String description, Instant dueDate) {
}
