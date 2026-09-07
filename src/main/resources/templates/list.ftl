<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>todo-spark</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
<h1>todo-spark</h1>

<form action="/todos" method="post" class="new-todo">
    <input type="text" name="title" placeholder="What needs doing?" required>
    <input type="text" name="description" placeholder="Description (optional)">
    <input type="date" name="dueDate">
    <button type="submit">Add</button>
</form>

<nav class="filters">
    <a href="/?status=all"<#if filter == "all"> class="active"</#if>>All</a>
    <a href="/?status=active"<#if filter == "active"> class="active"</#if>>Active</a>
    <a href="/?status=completed"<#if filter == "completed"> class="active"</#if>>Completed</a>
</nav>

<ul class="todos">
    <#list todos as todo>
    <li<#if todo.completed()> class="completed"</#if>>
        <form action="/todos/${todo.id()}/toggle" method="post" class="inline">
            <button type="submit" class="toggle" title="toggle complete">${todo.completed()?then("&#10003;", "&#9675;")}</button>
        </form>
        <span class="title">${todo.title()}</span>
        <#if todo.description()??><span class="description">${todo.description()}</span></#if>
        <#if todo.dueDate()??><span class="due-date">due ${todo.formattedDueDate}</span></#if>
        <a href="/todos/${todo.id()}/edit">edit</a>
        <form action="/todos/${todo.id()}/delete" method="post" class="inline">
            <button type="submit" class="delete">delete</button>
        </form>
    </li>
    <#else>
    <li class="empty">Nothing here.</li>
    </#list>
</ul>

<#if todos?size gt 0>
<form action="/todos/completed/delete" method="post">
    <button type="submit">Clear completed</button>
</form>
</#if>
<script src="/app.js"></script>
</body>
</html>
