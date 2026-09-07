package todo.spark.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.Instant;

/**
 * Shared Gson instance for the API, configured with an ISO-8601 adapter for {@link Instant}
 * since Gson has no built-in support for it.
 */
public final class Json {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private Json() {
    }
}
