package todo.spark.web;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Broadcasts a short, human-readable description of each {@link todo.spark.repository.TodoRepository}
 * change (e.g. "Added &quot;buy milk&quot;") to every connected client, so any other open browser
 * tab/window can show a toast and refresh. Spark creates one instance of this class per connection
 * (registered via the {@code Class<?>} overload of {@code webSocket}), so the set of live sessions
 * is tracked statically across all instances.
 */
@WebSocket
public class TodoWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(TodoWebSocket.class);
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    private Session session;

    @OnWebSocketOpen
    public void connected(Session session) {
        this.session = session;
        SESSIONS.add(session);
    }

    @OnWebSocketClose
    public void closed(int statusCode, String reason) {
        SESSIONS.remove(session);
        session = null;
    }

    public static void broadcast(String message) {
        for (Session session : SESSIONS) {
            // an unclean disconnect (e.g. the client's own location.reload()) can leave a
            // stale, already-closed session in SESSIONS before onWebSocketClose fires (or
            // without it firing at all) - skip and clean those up rather than trying to
            // send, which would just throw and leave the stale entry to fail again next time
            if (!session.isOpen()) {
                SESSIONS.remove(session);
                continue;
            }
            session.sendText(message, Callback.from(() -> { }, throwable -> {
                LOG.warn("Failed to send websocket message, removing stale session", throwable);
                SESSIONS.remove(session);
            }));
        }
    }
}
