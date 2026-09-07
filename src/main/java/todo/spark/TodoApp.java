package todo.spark;

import static spark.Spark.awaitInitialization;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import todo.spark.repository.TodoRepository;
import todo.spark.web.ExceptionHandlers;
import todo.spark.web.TodoApiRoutes;
import todo.spark.web.TodoUiRoutes;

public class TodoApp {

    private static final Logger LOG = LoggerFactory.getLogger(TodoApp.class);
    private static final int DEFAULT_PORT = 4567;

    public static void main(String[] args) {
        int appPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        port(appPort);
        staticFileLocation("/public");

        TodoRepository repository = new TodoRepository();

        get("/health", (request, response) -> "OK");
        new TodoApiRoutes(repository).register();
        new TodoUiRoutes(repository).register();
        ExceptionHandlers.register();

        awaitInitialization();
        LOG.info("todo-spark started on port {}", appPort);
    }
}
