package todo.spark.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        var first = repository.create("first", null, null);
        var second = repository.create("second", "details", Instant.now());

        assertThat(first.id()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(2);
        assertThat(first.completed()).isFalse();
        assertThat(second.completed()).isFalse();
        assertThat(second.description()).isEqualTo("details");
    }

    @Test
    void findById_whenExists_returnsTodo() {
        var created = repository.create("title", null, null);

        var found = repository.findById(created.id());

        assertThat(found).contains(created);
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

        var all = repository.findAll();

        assertThat(all).containsExactly(first, second, third);
    }

    @Test
    void findByCompleted_filtersByCompletionStatus() {
        var active = repository.create("active", null, null);
        var completed = repository.create("completed", null, null);
        // Todo is an immutable record, so toggling replaces the stored instance rather than
        // mutating "completed" in place - the returned value is the current one to compare against
        var toggled = repository.toggleCompleted(completed.id()).orElseThrow();

        assertThat(repository.findByCompleted(false)).containsExactly(active);
        assertThat(repository.findByCompleted(true)).containsExactly(toggled);
    }

    @Test
    void update_whenExists_updatesFieldsAndReturnsTodo() {
        var created = repository.create("original", "original description", null);
        var newDueDate = Instant.now();

        var updated = repository.update(created.id(), "updated", "updated description", newDueDate);

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
        var created = repository.create("title", null, null);

        var toggledOn = repository.toggleCompleted(created.id());
        assertThat(toggledOn).isPresent();
        assertThat(toggledOn.get().completed()).isTrue();

        var toggledOff = repository.toggleCompleted(created.id());
        assertThat(toggledOff).isPresent();
        assertThat(toggledOff.get().completed()).isFalse();
    }

    @Test
    void toggleCompleted_whenMissing_returnsEmpty() {
        assertThat(repository.toggleCompleted(999)).isEmpty();
    }

    @Test
    void delete_whenExists_removesAndReturnsTrue() {
        var created = repository.create("title", null, null);

        var deleted = repository.delete(created.id());

        assertThat(deleted).isTrue();
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_whenMissing_returnsFalse() {
        assertThat(repository.delete(999)).isFalse();
    }

    @Test
    void deleteCompleted_removesOnlyCompletedTodosAndReturnsCount() {
        var active = repository.create("active", null, null);
        var completedOne = repository.create("completed one", null, null);
        var completedTwo = repository.create("completed two", null, null);
        repository.toggleCompleted(completedOne.id());
        repository.toggleCompleted(completedTwo.id());

        var deletedCount = repository.deleteCompleted();

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
        var created = repository.create("original", null, null);
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
        var created = repository.create("title", null, null);
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
        var created = repository.create("gone soon", null, null);
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
        var onlyCompleted = repository.create("solo", null, null);
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
