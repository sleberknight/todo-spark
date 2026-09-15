package todo.spark.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import todo.spark.model.Todo;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class SqliteTodoRepositoryTest {

    private SqliteTodoRepository repository;
    private List<String> changeMessages;

    @BeforeEach
    void setUp() {
        // a fresh, private in-memory database per test - exercises the real SQL without disk I/O
        repository = new SqliteTodoRepository("jdbc:sqlite::memory:");
        changeMessages = new ArrayList<>();
        repository.setChangeListener(changeMessages::add);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void create_persistsAndNotifies() {
        var created = repository.create("buy milk", "2%", null);

        assertThat(created.id()).isPositive();
        assertThat(created.title()).isEqualTo("buy milk");
        assertThat(created.description()).isEqualTo("2%");
        assertThat(created.completed()).isFalse();
        assertThat(changeMessages).containsExactly("Added \"buy milk\"");
        assertThat(repository.findById(created.id())).contains(created);
    }

    @Test
    void findById_whenMissing_returnsEmpty() {
        assertThat(repository.findById(999)).isEmpty();
    }

    @Test
    void findAll_returnsTodosInCreationOrder() {
        var first = repository.create("first", null, null);
        var second = repository.create("second", null, null);
        var third = repository.create("third", null, null);

        assertThat(repository.findAll()).containsExactly(first, second, third);
    }

    @Test
    void findByCompleted_filtersByCompletionStatus() {
        var active = repository.create("active", null, null);
        var completed = repository.create("completed", null, null);
        var toggled = repository.toggleCompleted(completed.id()).orElseThrow();

        assertThat(repository.findByCompleted(false)).containsExactly(active);
        assertThat(repository.findByCompleted(true)).containsExactly(toggled);
    }

    @Test
    void update_whenExists_updatesFieldsAndNotifies() {
        var created = repository.create("original", "original description", null);
        var newDueDate = Instant.now();
        changeMessages.clear();

        var updated = repository.update(created.id(), "updated", "updated description", newDueDate);

        assertThat(updated).isPresent();
        assertThat(updated.get().title()).isEqualTo("updated");
        assertThat(updated.get().description()).isEqualTo("updated description");
        assertThat(updated.get().dueDate()).isEqualTo(newDueDate);
        assertThat(changeMessages).containsExactly("Updated \"updated\"");
    }

    @Test
    void update_whenMissing_returnsEmptyAndDoesNotNotify() {
        assertThat(repository.update(999, "title", null, null)).isEmpty();
        assertThat(changeMessages).isEmpty();
    }

    @Test
    void toggleCompleted_flipsStatusAndNotifies() {
        var created = repository.create("title", null, null);
        changeMessages.clear();

        var toggledOn = repository.toggleCompleted(created.id()).orElseThrow();
        assertThat(toggledOn.completed()).isTrue();

        var toggledOff = repository.toggleCompleted(created.id()).orElseThrow();
        assertThat(toggledOff.completed()).isFalse();

        assertThat(changeMessages).containsExactly("Completed \"title\"", "Reactivated \"title\"");
    }

    @Test
    void toggleCompleted_whenMissing_returnsEmptyAndDoesNotNotify() {
        assertThat(repository.toggleCompleted(999)).isEmpty();
        assertThat(changeMessages).isEmpty();
    }

    @Test
    void delete_whenExists_removesAndNotifies() {
        var created = repository.create("gone soon", null, null);
        changeMessages.clear();

        assertThat(repository.delete(created.id())).isTrue();
        assertThat(repository.findById(created.id())).isEmpty();
        assertThat(changeMessages).containsExactly("Deleted \"gone soon\"");
    }

    @Test
    void delete_whenMissing_returnsFalseAndDoesNotNotify() {
        assertThat(repository.delete(999)).isFalse();
        assertThat(changeMessages).isEmpty();
    }

    @Test
    void deleteCompleted_removesOnlyCompletedAndNotifiesWithCount() {
        var active = repository.create("active", null, null);
        var completedOne = repository.create("completed one", null, null);
        var completedTwo = repository.create("completed two", null, null);
        repository.toggleCompleted(completedOne.id());
        repository.toggleCompleted(completedTwo.id());
        changeMessages.clear();

        var deletedCount = repository.deleteCompleted();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(repository.findAll()).containsExactly(active);
        assertThat(changeMessages).containsExactly("Cleared 2 completed todos");
    }

    @Test
    void dataPersistsAcrossRepositoryInstancesBackedByTheSameFile() throws IOException {
        var dbFile = Files.createTempFile("todo-spark-test", ".db");
        Files.delete(dbFile); // SQLite creates the file itself; this just claims a unique path
        var jdbcUrl = "jdbc:sqlite:" + dbFile;

        try {
            try (var first = new SqliteTodoRepository(jdbcUrl)) {
                first.create("persisted todo", null, null);
            }

            try (var second = new SqliteTodoRepository(jdbcUrl)) {
                assertThat(second.findAll()).extracting(Todo::title).containsExactly("persisted todo");
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
