package todo.spark.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import todo.spark.model.Todo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class InMemoryTodoRepositoryTest {

    private TodoRepository repository;
    private List<String> changeMessages;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTodoRepository();
        changeMessages = new ArrayList<>();
        repository.setChangeListener(changeMessages::add);
    }

    @Test
    void create_assignsIncrementingIdsAndDefaultsToIncomplete() {
        Todo first = repository.create("first", null, null);
        Todo second = repository.create("second", "details", Instant.now());

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(first.completed()).isFalse();
        assertThat(second.completed()).isFalse();
        assertThat(second.description()).isEqualTo("details");
    }

    @Test
    void findById_whenExists_returnsTodo() {
        Todo created = repository.create("title", null, null);

        Optional<Todo> found = repository.findById(created.id());

        assertThat(found).contains(created);
    }

    @Test
    void findById_whenMissing_returnsEmpty() {
        assertThat(repository.findById(999)).isEmpty();
    }

    @Test
    void findAll_returnsTodosInCreationOrder() {
        Todo first = repository.create("first", null, null);
        Todo second = repository.create("second", null, null);
        Todo third = repository.create("third", null, null);

        List<Todo> all = repository.findAll();

        assertThat(all).containsExactly(first, second, third);
    }

    @Test
    void findByCompleted_filtersByCompletionStatus() {
        Todo active = repository.create("active", null, null);
        Todo completed = repository.create("completed", null, null);
        // Todo is an immutable record, so toggling replaces the stored instance rather than
        // mutating "completed" in place - the returned value is the current one to compare against
        Todo toggled = repository.toggleCompleted(completed.id()).orElseThrow();

        assertThat(repository.findByCompleted(false)).containsExactly(active);
        assertThat(repository.findByCompleted(true)).containsExactly(toggled);
    }

    @Test
    void update_whenExists_updatesFieldsAndReturnsTodo() {
        Todo created = repository.create("original", "original description", null);
        Instant newDueDate = Instant.now();

        Optional<Todo> updated = repository.update(created.id(), "updated", "updated description", newDueDate);

        assertThat(updated).isPresent();
        assertThat(updated.get().title()).isEqualTo("updated");
        assertThat(updated.get().description()).isEqualTo("updated description");
        assertThat(updated.get().dueDate()).isEqualTo(newDueDate);
    }

    @Test
    void update_whenMissing_returnsEmpty() {
        assertThat(repository.update(999, "title", null, null)).isEmpty();
    }

    @Test
    void toggleCompleted_flipsCompletionStatus() {
        Todo created = repository.create("title", null, null);

        Optional<Todo> toggledOn = repository.toggleCompleted(created.id());
        assertThat(toggledOn).isPresent();
        assertThat(toggledOn.get().completed()).isTrue();

        Optional<Todo> toggledOff = repository.toggleCompleted(created.id());
        assertThat(toggledOff).isPresent();
        assertThat(toggledOff.get().completed()).isFalse();
    }

    @Test
    void toggleCompleted_whenMissing_returnsEmpty() {
        assertThat(repository.toggleCompleted(999)).isEmpty();
    }

    @Test
    void delete_whenExists_removesAndReturnsTrue() {
        Todo created = repository.create("title", null, null);

        boolean deleted = repository.delete(created.id());

        assertThat(deleted).isTrue();
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_whenMissing_returnsFalse() {
        assertThat(repository.delete(999)).isFalse();
    }

    @Test
    void deleteCompleted_removesOnlyCompletedTodosAndReturnsCount() {
        Todo active = repository.create("active", null, null);
        Todo completedOne = repository.create("completed one", null, null);
        Todo completedTwo = repository.create("completed two", null, null);
        repository.toggleCompleted(completedOne.id());
        repository.toggleCompleted(completedTwo.id());

        int deletedCount = repository.deleteCompleted();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(repository.findAll()).containsExactly(active);
    }

    @Test
    void create_notifiesChangeListener() {
        repository.create("buy milk", null, null);

        assertThat(changeMessages).containsExactly("Added \"buy milk\"");
    }

    @Test
    void update_whenExists_notifiesChangeListener() {
        Todo created = repository.create("original", null, null);
        changeMessages.clear();

        repository.update(created.id(), "updated", null, null);

        assertThat(changeMessages).containsExactly("Updated \"updated\"");
    }

    @Test
    void update_whenMissing_doesNotNotify() {
        repository.update(999, "title", null, null);

        assertThat(changeMessages).isEmpty();
    }

    @Test
    void toggleCompleted_notifiesWithCompletedOrReactivatedWording() {
        Todo created = repository.create("title", null, null);
        changeMessages.clear();

        repository.toggleCompleted(created.id());
        repository.toggleCompleted(created.id());

        assertThat(changeMessages).containsExactly("Completed \"title\"", "Reactivated \"title\"");
    }

    @Test
    void toggleCompleted_whenMissing_doesNotNotify() {
        repository.toggleCompleted(999);

        assertThat(changeMessages).isEmpty();
    }

    @Test
    void delete_whenExists_notifiesChangeListener() {
        Todo created = repository.create("gone soon", null, null);
        changeMessages.clear();

        repository.delete(created.id());

        assertThat(changeMessages).containsExactly("Deleted \"gone soon\"");
    }

    @Test
    void delete_whenMissing_doesNotNotify() {
        repository.delete(999);

        assertThat(changeMessages).isEmpty();
    }

    @Test
    void deleteCompleted_notifiesWithCountAndCorrectPluralization() {
        Todo onlyCompleted = repository.create("solo", null, null);
        repository.toggleCompleted(onlyCompleted.id());
        changeMessages.clear();

        repository.deleteCompleted();

        assertThat(changeMessages).containsExactly("Cleared 1 completed todo");
    }

    @Test
    void deleteCompleted_whenNoneCompleted_doesNotNotify() {
        repository.create("still active", null, null);
        changeMessages.clear();

        repository.deleteCompleted();

        assertThat(changeMessages).isEmpty();
    }
}
