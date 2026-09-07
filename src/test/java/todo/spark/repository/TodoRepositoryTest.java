package todo.spark.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import todo.spark.model.Todo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class TodoRepositoryTest {

    private TodoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TodoRepository();
    }

    @Test
    void create_assignsIncrementingIdsAndDefaultsToIncomplete() {
        Todo first = repository.create("first", null, null);
        Todo second = repository.create("second", "details", Instant.now());

        assertThat(first.getId()).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(2);
        assertThat(first.isCompleted()).isFalse();
        assertThat(second.isCompleted()).isFalse();
        assertThat(second.getDescription()).isEqualTo("details");
    }

    @Test
    void findById_whenExists_returnsTodo() {
        Todo created = repository.create("title", null, null);

        Optional<Todo> found = repository.findById(created.getId());

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
        repository.toggleCompleted(completed.getId());

        assertThat(repository.findByCompleted(false)).containsExactly(active);
        assertThat(repository.findByCompleted(true)).containsExactly(completed);
    }

    @Test
    void update_whenExists_updatesFieldsAndReturnsTodo() {
        Todo created = repository.create("original", "original description", null);
        Instant newDueDate = Instant.now();

        Optional<Todo> updated = repository.update(created.getId(), "updated", "updated description", newDueDate);

        assertThat(updated).isPresent();
        assertThat(updated.get().getTitle()).isEqualTo("updated");
        assertThat(updated.get().getDescription()).isEqualTo("updated description");
        assertThat(updated.get().getDueDate()).isEqualTo(newDueDate);
    }

    @Test
    void update_whenMissing_returnsEmpty() {
        assertThat(repository.update(999, "title", null, null)).isEmpty();
    }

    @Test
    void toggleCompleted_flipsCompletionStatus() {
        Todo created = repository.create("title", null, null);

        Optional<Todo> toggledOn = repository.toggleCompleted(created.getId());
        assertThat(toggledOn).isPresent();
        assertThat(toggledOn.get().isCompleted()).isTrue();

        Optional<Todo> toggledOff = repository.toggleCompleted(created.getId());
        assertThat(toggledOff).isPresent();
        assertThat(toggledOff.get().isCompleted()).isFalse();
    }

    @Test
    void toggleCompleted_whenMissing_returnsEmpty() {
        assertThat(repository.toggleCompleted(999)).isEmpty();
    }

    @Test
    void delete_whenExists_removesAndReturnsTrue() {
        Todo created = repository.create("title", null, null);

        boolean deleted = repository.delete(created.getId());

        assertThat(deleted).isTrue();
        assertThat(repository.findById(created.getId())).isEmpty();
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
        repository.toggleCompleted(completedOne.getId());
        repository.toggleCompleted(completedTwo.getId());

        int deletedCount = repository.deleteCompleted();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(repository.findAll()).containsExactly(active);
    }
}
