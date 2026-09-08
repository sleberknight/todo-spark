package todo.spark.web;

import static org.assertj.core.api.Assertions.assertThat;
import static spark.Spark.awaitInitialization;
import static spark.Spark.awaitStop;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;
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
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

class TodoUiRoutesTest {

    private static final int TEST_PORT = 4569;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static TodoRepository repository;

    @BeforeAll
    static void startServer() {
        port(TEST_PORT);
        staticFileLocation("/public");
        repository = new InMemoryTodoRepository();
        new TodoUiRoutes(repository).register();
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
    void indexPage_rendersAndServesStaticCss() throws Exception {
        HttpResponse<String> page = get("/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("todo-spark");

        HttpResponse<String> css = get("/style.css");
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.body()).isNotBlank();
    }

    @Test
    void unknownPage_returnsHtmlNotFoundPage() throws Exception {
        HttpResponse<String> response = get("/no-such-page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/html");
        assertThat(response.body()).contains("<h1>404</h1>");
    }

    @Test
    void createTodo_withTitle_redirectsAndAppearsInList() throws Exception {
        HttpResponse<String> createResponse = postForm("/todos", Map.of(
                "title", "a unique creatable title",
                "description", "a unique description",
                "dueDate", "2026-12-25"));

        assertThat(createResponse.statusCode()).isEqualTo(302);
        assertThat(createResponse.headers().firstValue("Location").orElseThrow()).isEqualTo("/");

        HttpResponse<String> list = get("/");
        assertThat(list.body())
                .contains("a unique creatable title")
                .contains("a unique description")
                .contains("due 2026-12-25");
    }

    @Test
    void createTodo_withBlankTitle_redirectsWithoutError() throws Exception {
        HttpResponse<String> response = postForm("/todos", Map.of("title", "  "));

        assertThat(response.statusCode()).isEqualTo(302);
    }

    @Test
    void editPage_showsExistingValues() throws Exception {
        long id = createAndGetId("editable title", "editable description", "2026-11-01");

        HttpResponse<String> editPage = get("/todos/" + id + "/edit");

        assertThat(editPage.statusCode()).isEqualTo(200);
        assertThat(editPage.body())
                .contains("value=\"editable title\"")
                .contains("value=\"editable description\"")
                .contains("value=\"2026-11-01\"");
    }

    @Test
    void editPage_whenMissing_redirectsToIndex() throws Exception {
        HttpResponse<String> response = get("/todos/999999/edit");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo("/");
    }

    @Test
    void updateTodo_changesTitleAndDescription() throws Exception {
        long id = createAndGetId("before update", null, null);

        HttpResponse<String> response = postForm("/todos/" + id, Map.of(
                "title", "after update",
                "description", "new description"));

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> list = get("/");
        assertThat(list.body()).contains("after update").contains("new description").doesNotContain("before update");
    }

    @Test
    void toggleTodo_marksItCompletedInList() throws Exception {
        long id = createAndGetId("toggle me in ui", null, null);

        HttpResponse<String> response = post("/todos/" + id + "/toggle");

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> list = get("/");
        assertThat(list.body()).contains("class=\"completed\"").contains("toggle me in ui");
    }

    @Test
    void deleteTodo_removesItFromList() throws Exception {
        long id = createAndGetId("doomed todo", null, null);

        HttpResponse<String> response = post("/todos/" + id + "/delete");

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> list = get("/");
        assertThat(list.body()).doesNotContain("doomed todo");
    }

    @Test
    void clearCompleted_removesOnlyCompletedTodos() throws Exception {
        long activeId = createAndGetId("stays active in ui", null, null);
        long completedId = createAndGetId("gets cleared in ui", null, null);
        post("/todos/" + completedId + "/toggle");

        HttpResponse<String> response = post("/todos/completed/delete");

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> list = get("/");
        assertThat(list.body()).contains("stays active in ui").doesNotContain("gets cleared in ui");
    }

    private static long createAndGetId(String title, String description, String dueDate) throws IOException, InterruptedException {
        var form = new java.util.HashMap<String, String>();
        form.put("title", title);
        if (description != null) {
            form.put("description", description);
        }
        if (dueDate != null) {
            form.put("dueDate", dueDate);
        }
        postForm("/todos", form);

        return repository.findAll().stream()
                .filter(todo -> todo.title().equals(title))
                .max(Comparator.comparing(Todo::createdAt))
                .orElseThrow()
                .id();
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build();
        return HTTP_CLIENT.send(request, BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP_CLIENT.send(request, BodyHandlers.ofString());
    }

    private static HttpResponse<String> postForm(String path, Map<String, String> formFields) throws IOException, InterruptedException {
        String body = formFields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP_CLIENT.send(request, BodyHandlers.ofString());
    }
}
