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

// Menu groups in the top bar: one open at a time, closed by a click elsewhere or by Escape.
document.addEventListener('click', function (event) {
    var open = document.querySelectorAll('details.nav-group[open]');
    for (var i = 0; i < open.length; i++) {
        if (!open[i].contains(event.target)) {
            open[i].removeAttribute('open');
        }
    }
});
document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') {
        return;
    }
    var open = document.querySelectorAll('details.nav-group[open]');
    for (var i = 0; i < open.length; i++) {
        open[i].removeAttribute('open');
        open[i].querySelector('summary').focus();
    }
});
