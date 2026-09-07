(function () {
    var protocol = location.protocol === "https:" ? "wss:" : "ws:";
    var socket = new WebSocket(protocol + "//" + location.host + "/ws");

    socket.onmessage = function (event) {
        showToast(event.data);
        setTimeout(function () {
            location.reload();
        }, 1200);
    };

    function showToast(message) {
        var toast = document.getElementById("toast");
        if (!toast) {
            toast = document.createElement("div");
            toast.id = "toast";
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.classList.add("visible");
    }
})();
