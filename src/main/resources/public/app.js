(function () {
    var toastHideTimer;
    var reconnectDelayMs = 3000;

    connect();

    function connect() {
        var protocol = location.protocol === "https:" ? "wss:" : "ws:";
        var socket = new WebSocket(protocol + "//" + location.host + "/ws");

        socket.onmessage = function (event) {
            showToast(event.data);
            refreshTodoList();
        };

        // a connection can die at any time (idle timeout, network blip, server restart);
        // without this, live updates would just silently stop working until a manual reload
        socket.onclose = function () {
            setTimeout(connect, reconnectDelayMs);
        };

        socket.onerror = function () {
            socket.close();
        };
    }

    function showToast(message) {
        var toast = document.getElementById("toast");
        if (!toast) {
            toast = document.createElement("div");
            toast.id = "toast";
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.classList.add("visible");

        clearTimeout(toastHideTimer);
        toastHideTimer = setTimeout(function () {
            toast.classList.remove("visible");
        }, 3000);
    }

    function refreshTodoList() {
        var list = document.querySelector(".todos");
        if (!list) {
            return;
        }
        var status = new URLSearchParams(location.search).get("status") || "all";
        var url = status === "all" ? "/api/todos" : "/api/todos?status=" + status;

        fetch(url)
            .then(function (response) { return response.json(); })
            .then(function (todos) { renderTodoList(list, todos); })
            .catch(function (error) { console.error("Failed to refresh todo list", error); });
    }

    function renderTodoList(list, todos) {
        list.innerHTML = "";
        if (todos.length === 0) {
            var empty = document.createElement("li");
            empty.className = "empty";
            empty.textContent = "Nothing here.";
            list.appendChild(empty);
            return;
        }
        todos.forEach(function (todo) {
            list.appendChild(renderTodoItem(todo));
        });
    }

    // keep this in sync with the per-item markup in list.ftl - this is the one piece of
    // markup duplicated between the server-rendered template and the client, since a live
    // refresh re-renders from the JSON API instead of re-fetching the whole page
    function renderTodoItem(todo) {
        var li = document.createElement("li");
        if (todo.completed) {
            li.className = "completed";
        }

        var toggleForm = document.createElement("form");
        toggleForm.action = "/todos/" + todo.id + "/toggle";
        toggleForm.method = "post";
        toggleForm.className = "inline";
        var toggleButton = document.createElement("button");
        toggleButton.type = "submit";
        toggleButton.className = "toggle";
        toggleButton.title = "toggle complete";
        toggleButton.innerHTML = todo.completed ? "&#10003;" : "&#9675;";
        toggleForm.appendChild(toggleButton);
        li.appendChild(toggleForm);

        var title = document.createElement("span");
        title.className = "title";
        title.textContent = todo.title;
        li.appendChild(title);

        if (todo.description) {
            var description = document.createElement("span");
            description.className = "description";
            description.textContent = todo.description;
            li.appendChild(description);
        }

        if (todo.dueDate) {
            var dueDate = document.createElement("span");
            dueDate.className = "due-date";
            dueDate.textContent = "due " + todo.dueDate.slice(0, 10);
            li.appendChild(dueDate);
        }

        var editLink = document.createElement("a");
        editLink.href = "/todos/" + todo.id + "/edit";
        editLink.textContent = "edit";
        li.appendChild(editLink);

        var deleteForm = document.createElement("form");
        deleteForm.action = "/todos/" + todo.id + "/delete";
        deleteForm.method = "post";
        deleteForm.className = "inline";
        var deleteButton = document.createElement("button");
        deleteButton.type = "submit";
        deleteButton.className = "delete";
        deleteButton.textContent = "delete";
        deleteForm.appendChild(deleteButton);
        li.appendChild(deleteForm);

        return li;
    }
})();
