package todo.spark.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import spark.testing.SparkServerExtension;
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

    @RegisterExtension
    static final SparkServerExtension SPARK = new SparkServerExtension(http -> {
        http.port(TEST_PORT);
        http.staticFileLocation("/public");
        var repository = new InMemoryTodoRepository();
        repository.setChangeListener(TodoWebSocket::broadcast);
        http.webSocket("/ws", TodoWebSocket.class);
        new TodoApiRoutes(repository).register(http);
        new TodoUiRoutes(repository).register(http);
        Filters.register(http);
        ExceptionHandlers.register(http);
    });

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void stopBrowser() {
        browser.close();
        playwright.close();
    }

    @Test
    void addingATodo_showsItInTheList() {
        try (var page = browser.newPage()) {
            page.navigate(BASE_URL);

            page.fill(".new-todo input[name=title]", "buy milk via playwright");
            page.locator(".new-todo button[type=submit]").click();

            assertThat(page.locator(".todos")).containsText("buy milk via playwright");
        }
    }

    @Test
    void togglingATodo_marksItCompleted() {
        try (var page = browser.newPage()) {
            page.navigate(BASE_URL);

            page.fill(".new-todo input[name=title]", "toggle via playwright");
            page.locator(".new-todo button[type=submit]").click();
            page.locator("li:has-text('toggle via playwright') >> button.toggle").click();

            assertThat(page.locator("li:has-text('toggle via playwright')")).hasClass("completed");
        }
    }

    @Test
    void liveUpdate_reachesOtherOpenTabWithoutNavigatingAway() {
        try (var tab1 = browser.newPage(); var tab2 = browser.newPage()) {
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
