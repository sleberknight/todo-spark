package todo.spark.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static spark.Spark.awaitInitialization;
import static spark.Spark.awaitStop;
import static spark.Spark.port;
import static spark.Spark.staticFileLocation;
import static spark.Spark.stop;
import static spark.Spark.webSocket;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import todo.spark.repository.TodoRepository;
import todo.spark.repository.InMemoryTodoRepository;

/**
 * Real-browser tests via Playwright, covering behavior the HTTP-only tests in
 * TodoUiRoutesTest can't reach: actual JS execution, the WebSocket connection, and
 * whether a page did or didn't navigate.
 */
class TodoBrowserTest {

    private static final int TEST_PORT = 4572;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT + "/";

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void startServerAndBrowser() {
        port(TEST_PORT);
        staticFileLocation("/public");
        TodoRepository repository = new InMemoryTodoRepository();
        repository.setChangeListener(TodoWebSocket::broadcast);
        webSocket("/ws", TodoWebSocket.class);
        new TodoApiRoutes(repository).register();
        new TodoUiRoutes(repository).register();
        Filters.register();
        ExceptionHandlers.register();
        awaitInitialization();

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void stopServerAndBrowser() {
        browser.close();
        playwright.close();
        stop();
        awaitStop();
    }

    @Test
    void addingATodo_showsItInTheList() {
        try (Page page = browser.newPage()) {
            page.navigate(BASE_URL);

            page.fill(".new-todo input[name=title]", "buy milk via playwright");
            page.locator(".new-todo button[type=submit]").click();

            assertThat(page.locator(".todos")).containsText("buy milk via playwright");
        }
    }

    @Test
    void togglingATodo_marksItCompleted() {
        try (Page page = browser.newPage()) {
            page.navigate(BASE_URL);

            page.fill(".new-todo input[name=title]", "toggle via playwright");
            page.locator(".new-todo button[type=submit]").click();
            page.locator("li:has-text('toggle via playwright') >> button.toggle").click();

            assertThat(page.locator("li:has-text('toggle via playwright')")).hasClass("completed");
        }
    }

    @Test
    void liveUpdate_reachesOtherOpenTabWithoutNavigatingAway() {
        try (Page tab1 = browser.newPage(); Page tab2 = browser.newPage()) {
            tab1.navigate(BASE_URL);
            tab2.navigate(BASE_URL);

            // if tab2 ever navigates (rather than updating in place), this gets wiped out -
            // that's how we prove the live refresh really doesn't reload the page
            tab2.evaluate("window.__testMarker = true");

            tab1.fill(".new-todo input[name=title]", "live update via playwright");
            tab1.locator(".new-todo button[type=submit]").click();

            assertThat(tab2.locator("#toast")).containsText("Added \"live update via playwright\"");
            assertThat(tab2.locator(".todos")).containsText("live update via playwright");
            assertEquals(Boolean.TRUE, tab2.evaluate("window.__testMarker"));
        }
    }
}
