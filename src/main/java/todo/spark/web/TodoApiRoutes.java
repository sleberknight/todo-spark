package todo.spark.web;

import static java.util.Objects.isNull;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.patch;
import static spark.Spark.path;
import static spark.Spark.post;
import static spark.Spark.put;

import spark.Request;
import spark.Response;
import todo.spark.model.Todo;
import todo.spark.repository.TodoRepository;

import java.util.List;

public class TodoApiRoutes {

    private final TodoRepository repository;

    public TodoApiRoutes(TodoRepository repository) {
        this.repository = repository;
    }

    public void register() {
        var json = new JsonTransformer();

        path("/api/todos", () -> {
            get("", this::listTodos, json);
            post("", this::createTodo, json);

            // registered before "/:id" so it isn't shadowed by that wildcard segment
            delete("/completed", this::deleteCompleted, json);

            get("/:id", this::getTodo, json);
            put("/:id", this::updateTodo, json);
            patch("/:id/toggle", this::toggleTodo, json);
            delete("/:id", this::deleteTodo, json);
        });
    }

    private List<Todo> listTodos(Request request, Response response) {
        var status = request.queryParams("status");
        if (isNull(status) || status.equals("all")) {
            return repository.findAll();
        }
        return repository.findByCompleted("completed".equals(status));
    }

    private Object createTodo(Request request, Response response) {
        var body = Json.GSON.fromJson(request.body(), TodoRequest.class);
        if (isNull(body) || isBlank(body.title())) {
            response.status(400);
            return new ErrorResponse("title is required");
        }
        var created = repository.create(body.title(), body.description(), body.dueDate());
        response.status(201);
        return created;
    }

    private Object getTodo(Request request, Response response) {
        var id = Long.parseLong(request.params(":id"));
        return repository.findById(id)
                .<Object>map(todo -> todo)
                .orElseGet(() -> notFound(response));
    }

    private Object updateTodo(Request request, Response response) {
        var id = Long.parseLong(request.params(":id"));
        var body = Json.GSON.fromJson(request.body(), TodoRequest.class);
        if (isNull(body) || isBlank(body.title())) {
            response.status(400);
            return new ErrorResponse("title is required");
        }
        return repository.update(id, body.title(), body.description(), body.dueDate())
                .<Object>map(todo -> todo)
                .orElseGet(() -> notFound(response));
    }

    private Object toggleTodo(Request request, Response response) {
        var id = Long.parseLong(request.params(":id"));
        return repository.toggleCompleted(id)
                .<Object>map(todo -> todo)
                .orElseGet(() -> notFound(response));
    }

    private Object deleteTodo(Request request, Response response) {
        var id = Long.parseLong(request.params(":id"));
        if (!repository.delete(id)) {
            return notFound(response);
        }
        response.status(204);
        return "";
    }

    private Object deleteCompleted(Request request, Response response) {
        var deletedCount = repository.deleteCompleted();
        return new DeletedCountResponse(deletedCount);
    }

    private static Object notFound(Response response) {
        response.status(404);
        return new ErrorResponse("todo not found");
    }

    private static boolean isBlank(String s) {
        return isNull(s) || s.isBlank();
    }

    private record DeletedCountResponse(int deletedCount) {
    }
}
