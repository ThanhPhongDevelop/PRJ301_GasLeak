// Asks for confirmation before any form or link that carries data-confirm (delete, lock, control commands).
document.addEventListener('submit', function (event) {
    var message = event.target.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
document.addEventListener('click', function (event) {
    var link = event.target.closest ? event.target.closest('a[data-confirm]') : null;
    if (link && !window.confirm(link.getAttribute('data-confirm'))) {
        event.preventDefault();
    }
});
