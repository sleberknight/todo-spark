package todo.spark.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

public final class Filters {

    private static final Logger LOG = LoggerFactory.getLogger(Filters.class);

    private Filters() {
    }

    public static void register(Service http) {
        http.before((request, response) -> LOG.debug("--> {} {}", request.requestMethod(), request.uri()));
        http.after((request, response) -> LOG.debug("<-- {} {} {}", request.requestMethod(), request.uri(), response.status()));

        http.before("/api/*", (request, response) -> response.type("application/json"));
    }
}
