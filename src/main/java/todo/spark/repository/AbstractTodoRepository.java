package todo.spark.repository;

import java.util.function.Consumer;

abstract class AbstractTodoRepository implements TodoRepository {

    private Consumer<String> changeListener = message -> { };

    @Override
    public void setChangeListener(Consumer<String> changeListener) {
        this.changeListener = changeListener;
    }

    protected void notifyChange(String message) {
        changeListener.accept(message);
    }
}
