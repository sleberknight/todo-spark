package todo.spark.web;

import static spark.Spark.after;
import static spark.Spark.before;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Filters {

    private static final Logger LOG = LoggerFactory.getLogger(Filters.class);

    private Filters() {
    }

    public static void register() {
        before((request, response) -> LOG.debug("--> {} {}", request.requestMethod(), request.uri()));
        after((request, response) -> LOG.debug("<-- {} {} {}", request.requestMethod(), request.uri(), response.status()));

        before("/api/*", (request, response) -> response.type("application/json"));
    }
}
