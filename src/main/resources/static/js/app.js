(function () {
    'use strict';

    /* ---------- HTMX: attach CSRF token to every request ---------- */
    document.body.addEventListener('htmx:configRequest', function (e) {
        var meta = document.querySelector('meta[name="_csrf"]');
        if (meta && meta.content) {
            e.detail.headers['X-CSRF-TOKEN'] = meta.content;
        }
    });

    /* ---------- Toasts ---------- */
    var toastTimers = new WeakMap();

    function showToast(message, type) {
        var container = document.getElementById('toast-container');
        if (!container) {
            return;
        }
        type = type || 'success';
        var el = document.createElement('div');
        el.className = 'toast toast-' + type;
        el.setAttribute('role', 'status');
        var text = document.createElement('span');
        text.textContent = message;
        var close = document.createElement('button');
        close.type = 'button';
        close.setAttribute('aria-label', 'Dismiss');
        close.textContent = '\u00D7';
        close.addEventListener('click', function () {
            removeToast(el);
        });
        el.appendChild(text);
        el.appendChild(close);
        container.appendChild(el);
        toastTimers.set(el, setTimeout(function () {
            removeToast(el);
        }, 4000));
    }

    function removeToast(el) {
        var t = toastTimers.get(el);
        if (t) {
            clearTimeout(t);
        }
        el.classList.add('hide');
        setTimeout(function () {
            el.remove();
        }, 220);
    }

    // Server-driven toasts via HX-Trigger response header: {"toast": {"message": "...", "type": "success|error|info"}}
    document.body.addEventListener('toast', function (e) {
        var d = e.detail || {};
        if (typeof d === 'string') {
            showToast(d);
        } else {
            showToast(d.message || 'Done', d.type || 'success');
        }
    });

    document.body.addEventListener('htmx:responseError', function () {
        showToast('Something went wrong. Please try again.', 'error');
    });

    /* ---------- Confirmation dialog for destructive actions ---------- */
    // Forms declared as <form data-confirm="message" data-confirm-title="..."> open the dialog first.
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || form.nodeType !== 1 || !form.matches || !form.matches('form[data-confirm]')) {
            return;
        }
        e.preventDefault();
        e.stopImmediatePropagation();
        openConfirm(
            form.getAttribute('data-confirm-title') || 'Are you sure?',
            form.getAttribute('data-confirm'),
            function () {
                form.submit();
            }
        );
    }, true);

    function openConfirm(title, message, onOk) {
        var dialog = document.getElementById('confirm-dialog');
        if (!dialog) {
            onOk();
            return;
        }
        document.getElementById('confirm-title').textContent = title;
        document.getElementById('confirm-message').textContent = message;
        var ok = document.getElementById('confirm-ok');
        var cancel = document.getElementById('confirm-cancel');
        var onOkClick = function () {
            cleanup();
            dialog.close();
            onOk();
        };
        var onCancelClick = function () {
            cleanup();
            dialog.close();
        };
        function cleanup() {
            ok.removeEventListener('click', onOkClick);
            cancel.removeEventListener('click', onCancelClick);
        }
        ok.addEventListener('click', onOkClick);
        cancel.addEventListener('click', onCancelClick);
        dialog.showModal();
    }

    /* ---------- Button loading states for HTMX requests ---------- */
    document.body.addEventListener('htmx:beforeRequest', function (e) {
        var btn = e.detail.elt.closest ? e.detail.elt.closest('button') : null;
        if (btn && btn.disabled !== undefined && btn.hasAttribute('data-loading')) {
            btn.disabled = true;
            var spinner = document.createElement('span');
            spinner.className = 'spinner';
            btn.prepend(spinner);
            setTimeout(function () { // safety: never leave a button stuck
                btn.disabled = false;
                if (spinner.parentNode) {
                    spinner.remove();
                }
            }, 8000);
        }
    });

    window.showToast = showToast;
})();
