package todo.spark;

import static spark.Spark.awaitInitialization;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;
import static spark.Spark.webSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import todo.spark.repository.TodoRepository;
import todo.spark.web.ExceptionHandlers;
import todo.spark.web.Filters;
import todo.spark.web.TodoApiRoutes;
import todo.spark.web.TodoUiRoutes;
import todo.spark.web.TodoWebSocket;

public class TodoApp {

    private static final Logger LOG = LoggerFactory.getLogger(TodoApp.class);
    private static final int DEFAULT_PORT = 4567;

    public static void main(String[] args) {
        int appPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        port(appPort);
        staticFileLocation("/public");

        TodoRepository repository = new TodoRepository();
        repository.setChangeListener(TodoWebSocket::broadcast);

        // webSocket(...) must be registered before anything that can trigger Spark's lazy
        // init (any addRoute call, e.g. get/post/etc.) - init() spawns a background thread
        // that reads the webSocketHandlers map to configure Jetty before starting it, and
        // that read isn't ordered against later webSocket() calls from the main thread
        webSocket("/ws", TodoWebSocket.class);

        get("/health", (request, response) -> "OK");
        new TodoApiRoutes(repository).register();
        new TodoUiRoutes(repository).register();
        Filters.register();
        ExceptionHandlers.register();

        awaitInitialization();
        LOG.info("todo-spark started on port {}", appPort);
    }
}
