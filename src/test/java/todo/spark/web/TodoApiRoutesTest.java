package todo.spark.web;

import static org.assertj.core.api.Assertions.assertThat;
import static spark.Spark.awaitInitialization;
import static spark.Spark.awaitStop;
import static spark.Spark.port;
import static spark.Spark.stop;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import todo.spark.model.Todo;
import todo.spark.repository.TodoRepository;
import todo.spark.repository.InMemoryTodoRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class TodoApiRoutesTest {

    private static final int TEST_PORT = 4568;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT + "/api/todos";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        port(TEST_PORT);
        new TodoApiRoutes(new InMemoryTodoRepository()).register();
        Filters.register();
        ExceptionHandlers.register();
        awaitInitialization();
    }

    @AfterAll
    static void stopServer() {
        stop();
        awaitStop();
    }

    @Test
    void createTodo_withValidTitle_returns201WithCreatedTodo() throws Exception {
        HttpResponse<String> response = post(BASE_URL, """
                {"title": "buy milk", "description": "2%"}""");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        Todo created = Json.GSON.fromJson(response.body(), Todo.class);
        assertThat(created.title()).isEqualTo("buy milk");
        assertThat(created.description()).isEqualTo("2%");
        assertThat(created.completed()).isFalse();
    }

    @Test
    void createTodo_withBlankTitle_returns400() throws Exception {
        HttpResponse<String> response = post(BASE_URL, """
                {"title": "  "}""");

        assertThat(response.statusCode()).isEqualTo(400);
        ErrorResponse error = Json.GSON.fromJson(response.body(), ErrorResponse.class);
        assertThat(error.message()).isEqualTo("title is required");
    }

    @Test
    void getTodo_whenExists_returnsIt() throws Exception {
        Todo created = createViaApi("get me");

        HttpResponse<String> response = get(BASE_URL + "/" + created.id());

        assertThat(response.statusCode()).isEqualTo(200);
        Todo fetched = Json.GSON.fromJson(response.body(), Todo.class);
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.title()).isEqualTo("get me");
    }

    @Test
    void getTodo_whenMissing_returns404() throws Exception {
        HttpResponse<String> response = get(BASE_URL + "/999999");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void getTodo_whenIdNotNumeric_returns400() throws Exception {
        HttpResponse<String> response = get(BASE_URL + "/not-a-number");

        assertThat(response.statusCode()).isEqualTo(400);
        ErrorResponse error = Json.GSON.fromJson(response.body(), ErrorResponse.class);
        assertThat(error.message()).isEqualTo("id must be numeric");
    }

    @Test
    void updateTodo_whenExists_updatesFields() throws Exception {
        Todo created = createViaApi("original title");

        HttpResponse<String> response = put(BASE_URL + "/" + created.id(), """
                {"title": "updated title", "description": "updated description"}""");

        assertThat(response.statusCode()).isEqualTo(200);
        Todo updated = Json.GSON.fromJson(response.body(), Todo.class);
        assertThat(updated.title()).isEqualTo("updated title");
        assertThat(updated.description()).isEqualTo("updated description");
    }

    @Test
    void updateTodo_whenMissing_returns404() throws Exception {
        HttpResponse<String> response = put(BASE_URL + "/999999", """
                {"title": "doesn't matter"}""");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void toggleTodo_flipsCompletionEachCall() throws Exception {
        Todo created = createViaApi("toggle me");

        HttpResponse<String> firstToggle = patch(BASE_URL + "/" + created.id() + "/toggle");
        assertThat(firstToggle.statusCode()).isEqualTo(200);
        assertThat(Json.GSON.fromJson(firstToggle.body(), Todo.class).completed()).isTrue();

        HttpResponse<String> secondToggle = patch(BASE_URL + "/" + created.id() + "/toggle");
        assertThat(secondToggle.statusCode()).isEqualTo(200);
        assertThat(Json.GSON.fromJson(secondToggle.body(), Todo.class).completed()).isFalse();
    }

    @Test
    void deleteTodo_whenExists_returns204AndRemovesIt() throws Exception {
        Todo created = createViaApi("delete me");

        HttpResponse<String> deleteResponse = delete(BASE_URL + "/" + created.id());
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> getResponse = get(BASE_URL + "/" + created.id());
        assertThat(getResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void deleteTodo_whenMissing_returns404() throws Exception {
        HttpResponse<String> response = delete(BASE_URL + "/999999");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void listTodos_filtersByStatus() throws Exception {
        Todo active = createViaApi("active status filter test");
        Todo completed = createViaApi("completed status filter test");
        patch(BASE_URL + "/" + completed.id() + "/toggle");

        HttpResponse<String> activeResponse = get(BASE_URL + "?status=active");
        HttpResponse<String> completedResponse = get(BASE_URL + "?status=completed");

        Todo[] activeTodos = Json.GSON.fromJson(activeResponse.body(), Todo[].class);
        Todo[] completedTodos = Json.GSON.fromJson(completedResponse.body(), Todo[].class);

        assertThat(activeTodos).extracting(Todo::id).contains(active.id()).doesNotContain(completed.id());
        assertThat(completedTodos).extracting(Todo::id).contains(completed.id()).doesNotContain(active.id());
    }

    @Test
    void unknownApiPath_returnsJsonNotFound() throws Exception {
        HttpResponse<String> response = get("http://localhost:" + TEST_PORT + "/api/no-such-path");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        ErrorResponse error = Json.GSON.fromJson(response.body(), ErrorResponse.class);
        assertThat(error.message()).isEqualTo("not found");
    }

    @Test
    void listTodos_returnsJsonContentType() throws Exception {
        HttpResponse<String> response = get(BASE_URL);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
    }

    @Test
    void deleteCompleted_removesOnlyCompletedTodos() throws Exception {
        Todo active = createViaApi("stays active");
        Todo completed = createViaApi("gets bulk-deleted");
        patch(BASE_URL + "/" + completed.id() + "/toggle");

        HttpResponse<String> response = delete(BASE_URL + "/completed");
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(get(BASE_URL + "/" + active.id()).statusCode()).isEqualTo(200);
        assertThat(get(BASE_URL + "/" + completed.id()).statusCode()).isEqualTo(404);
    }

    private static Todo createViaApi(String title) throws IOException, InterruptedException {
        HttpResponse<String> response = post(BASE_URL, "{\"title\": \"" + title + "\"}");
        return Json.GSON.fromJson(response.body(), Todo.class);
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> patch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).DELETE().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
