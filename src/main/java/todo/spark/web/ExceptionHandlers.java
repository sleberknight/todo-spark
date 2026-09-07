package todo.spark.web;

import static spark.Spark.exception;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExceptionHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlers.class);

    private ExceptionHandlers() {
    }

    public static void register() {
        exception(NumberFormatException.class, (e, request, response) -> {
            response.status(400);
            response.type("application/json");
            response.body(Json.GSON.toJson(new ErrorResponse("id must be numeric")));
        });

        exception(Exception.class, (e, request, response) -> {
            LOG.error("Unhandled exception handling {} {}", request.requestMethod(), request.pathInfo(), e);
            response.status(500);
            response.type("application/json");
            response.body(Json.GSON.toJson(new ErrorResponse("internal server error")));
        });

        notFound((request, response) -> {
            response.type("application/json");
            return Json.GSON.toJson(new ErrorResponse("not found"));
        });

        internalServerError((request, response) -> {
            response.type("application/json");
            return Json.GSON.toJson(new ErrorResponse("internal server error"));
        });
    }
}
