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
import todo.spark.model.Todo;
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
        repository = new TodoRepository();
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
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocket socket = connect(received);
        try {
            repository.create("buy milk", null, null);

            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("Added \"buy milk\""));
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void broadcastsWhenATodoIsDeleted() throws Exception {
        Todo created = repository.create("delete me via ws", null, null);
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocket socket = connect(received);
        try {
            repository.delete(created.id());

            await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("Deleted \"delete me via ws\""));
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    private static WebSocket connect(List<String> received) throws Exception {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                received.add(data.toString());
                webSocket.request(1);
                return null;
            }
        };
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + TEST_PORT + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);
    }
}
