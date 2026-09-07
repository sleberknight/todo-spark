package todo.spark.web;

import static spark.Spark.exception;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public final class ExceptionHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlers.class);

    private ExceptionHandlers() {
    }

    public static void register() {
        exception(NumberFormatException.class, (e, request, response) ->
                respondWithError(request, response, 400, "id must be numeric"));

        exception(Exception.class, (e, request, response) -> {
            LOG.error("Unhandled exception handling {} {}", request.requestMethod(), request.uri(), e);
            respondWithError(request, response, 500, "internal server error");
        });

        notFound((request, response) -> errorBody(request, response, 404, "not found"));
        internalServerError((request, response) -> errorBody(request, response, 500, "internal server error"));
    }

    private static void respondWithError(Request request, Response response, int status, String message) {
        response.body(errorBody(request, response, status, message));
    }

    private static String errorBody(Request request, Response response, int status, String message) {
        response.status(status);
        if (isApiRequest(request)) {
            response.type("application/json");
            return Json.GSON.toJson(new ErrorResponse(message));
        }
        response.type("text/html");
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Error %d</title><link rel="stylesheet" href="/style.css"></head>
                <body>
                <h1>%d</h1>
                <p>%s</p>
                <a href="/">Back to todos</a>
                </body>
                </html>
                """.formatted(status, status, message);
    }

    private static boolean isApiRequest(Request request) {
        return request.uri() != null && request.uri().startsWith("/api/");
    }
}
