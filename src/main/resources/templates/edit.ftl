<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Edit todo &mdash; todo-spark</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
<h1>Edit todo</h1>

<form action="/todos/${todo.id()}" method="post" class="edit-todo">
    <label>Title
        <input type="text" name="title" value="${todo.title()}" required>
    </label>
    <label>Description
        <input type="text" name="description" value="${(todo.description())!""}">
    </label>
    <label>Due date
        <input type="date" name="dueDate" value="${todo.formattedDueDate}">
    </label>
    <button type="submit">Save</button>
    <a href="/">Cancel</a>
</form>
</body>
</html>
