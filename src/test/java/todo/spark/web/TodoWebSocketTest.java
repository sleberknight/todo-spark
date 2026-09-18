package todo.spark.web;

import static org.awaitility.Awaitility.await;
import static spark.Spark.awaitInitialization;
import static spark.Spark.awaitStop;
import static spark.Spark.init;
import static spark.Spark.port;
import static spark.Spark.stop;
import static spark.Spark.webSocket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import todo.spark.repository.InMemoryTodoRepository;
import todo.spark.repository.TodoRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that repository changes actually reach a connected WebSocket client end to end.
 * Exact message wording for every repository operation is covered more cheaply at the unit
 * level in TodoRepositoryTest; this only needs to prove the wire actually works.
 */
class TodoWebSocketTest {

    private static final int TEST_PORT = 4570;
    private static TodoRepository repository;

    @BeforeAll
    static void startServer() {
        port(TEST_PORT);
        repository = new InMemoryTodoRepository();
        repository.setChangeListener(TodoWebSocket::broadcast);
        webSocket("/ws", TodoWebSocket.class);
        init();
        awaitInitialization();
    }

    @AfterAll
    static void stopServer() {
        stop();
        awaitStop();
    }

    @Test
    void broadcastsWhenATodoIsCreated() throws Exception {
        var received = new CopyOnWriteArrayList<String>();
        var webSocket = connect(received);
        try {
            repository.create("buy milk", null, null);

            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("Added \"buy milk\""));
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void broadcastsWhenATodoIsDeleted() throws Exception {
        var created = repository.create("delete me via ws", null, null);
        var received = new CopyOnWriteArrayList<String>();
        var webSocket = connect(received);
        try {
            repository.delete(created.id());

            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("Deleted \"delete me via ws\""));
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * The client-side handshake future completing only means the HTTP 101 response was
     * received - it says nothing about whether the server has run its @OnWebSocketOpen
     * callback and registered the session yet. A repository change triggered before that
     * registration happens is broadcast to a session set that doesn't include this connection
     * yet, and is lost rather than delayed, so waiting longer afterward wouldn't help. Waiting
     * here for the server to actually register the session closes that race at its source,
     * rather than retrying the trigger and hoping some attempt lands after registration.
     * <p>
     * Waits on {@link TodoWebSocket#totalConnections()} - a counter that only ever goes up -
     * rather than the live session count: a prior test's connection can still be draining
     * through its own async close handshake (sendClose()'s future resolves once the client
     * considers the close done, before the server has necessarily run @OnWebSocketClose and
     * removed the session) right as this one opens, so a live-count comparison, baseline or
     * not, can land back on the same number it started at - one session closing and another
     * opening cancel out numerically even though a new connection genuinely registered.
     */
    private static WebSocket connect(List<String> received) throws Exception {
        var baselineTotal = TodoWebSocket.totalConnections();
        var listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                received.add(data.toString());
                webSocket.request(1);
                return null;
            }
        };
        var socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + TEST_PORT + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> TodoWebSocket.totalConnections() > baselineTotal);
        return socket;
    }
}
