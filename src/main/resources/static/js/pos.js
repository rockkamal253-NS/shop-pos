(function () {
    'use strict';

    var form = document.getElementById('scan-form');
    if (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
        });
    }

    // Enter in the scan box posts /pos/scan (exact match => added to cart).
    var input = document.getElementById('scan-input');
    if (input) {
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                htmx.ajax('POST', '/pos/scan', {target: '#results-area', swap: 'innerHTML', values: {q: input.value}})
                    .then(function () {
                        input.value = '';
                        input.focus();
                    });
            }
        });
    }

    // Keep the scan box ready after every results swap (scanner workflow).
    document.body.addEventListener('htmx:afterSwap', function (e) {
        if (e.detail && e.detail.target && e.detail.target.id === 'results-area') {
            var s = document.getElementById('scan-input');
            if (s) {
                s.focus();
            }
        }
    });
})();
