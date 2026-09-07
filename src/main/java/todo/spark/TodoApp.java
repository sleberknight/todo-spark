package todo.spark;

import static spark.Spark.awaitInitialization;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TodoApp {

    private static final Logger LOG = LoggerFactory.getLogger(TodoApp.class);
    private static final int DEFAULT_PORT = 4567;

    public static void main(String[] args) {
        int appPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        port(appPort);
        staticFileLocation("/public");

        get("/health", (request, response) -> "OK");

        awaitInitialization();
        LOG.info("todo-spark started on port {}", appPort);
    }
}
