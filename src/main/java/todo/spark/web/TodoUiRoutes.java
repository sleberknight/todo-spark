package todo.spark.web;

import static spark.Spark.get;
import static spark.Spark.path;
import static spark.Spark.post;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import todo.spark.model.Todo;
import todo.spark.repository.TodoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class TodoUiRoutes {

    private final TodoRepository repository;

    public TodoUiRoutes(TodoRepository repository) {
        this.repository = repository;
    }

    public void register() {
        FreeMarkerEngine engine = new FreeMarkerEngine();

        get("/", this::listTodos, engine);
        get("/todos/:id/edit", this::editTodoForm, engine);

        path("/todos", () -> {
            post("", this::createTodo);

            // registered before "/:id" routes so it isn't shadowed by that wildcard segment
            post("/completed/delete", this::deleteCompleted);

            post("/:id", this::updateTodo);
            post("/:id/toggle", this::toggleTodo);
            post("/:id/delete", this::deleteTodo);
        });
    }

    private ModelAndView listTodos(Request request, Response response) {
        String status = request.queryParams("status");
        List<Todo> todos = switch (status == null ? "all" : status) {
            case "active" -> repository.findByCompleted(false);
            case "completed" -> repository.findByCompleted(true);
            default -> repository.findAll();
        };
        return new ModelAndView(Map.of("todos", todos, "filter", status == null ? "all" : status), "list.ftl");
    }

    private ModelAndView editTodoForm(Request request, Response response) {
        long id = Long.parseLong(request.params(":id"));
        return repository.findById(id)
                .map(todo -> new ModelAndView(Map.of("todo", todo), "edit.ftl"))
                .orElseGet(() -> {
                    response.redirect("/");
                    return null;
                });
    }

    private Object createTodo(Request request, Response response) {
        String title = request.queryParams("title");
        if (title != null && !title.isBlank()) {
            repository.create(title, blankToNull(request.queryParams("description")), parseDueDate(request.queryParams("dueDate")));
        }
        response.redirect("/");
        return null;
    }

    private Object updateTodo(Request request, Response response) {
        long id = Long.parseLong(request.params(":id"));
        String title = request.queryParams("title");
        if (title != null && !title.isBlank()) {
            repository.update(id, title, blankToNull(request.queryParams("description")), parseDueDate(request.queryParams("dueDate")));
        }
        response.redirect("/");
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Instant parseDueDate(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.isBlank()) {
            return null;
        }
        return LocalDate.parse(yyyyMmDd).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Object toggleTodo(Request request, Response response) {
        long id = Long.parseLong(request.params(":id"));
        repository.toggleCompleted(id);
        response.redirect("/");
        return null;
    }

    private Object deleteTodo(Request request, Response response) {
        long id = Long.parseLong(request.params(":id"));
        repository.delete(id);
        response.redirect("/");
        return null;
    }

    private Object deleteCompleted(Request request, Response response) {
        repository.deleteCompleted();
        response.redirect("/");
        return null;
    }
}
