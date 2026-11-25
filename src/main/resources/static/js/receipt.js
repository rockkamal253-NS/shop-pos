(function () {
    'use strict';

    var printBtn = document.getElementById('print-receipt-btn');
    if (printBtn) {
        printBtn.addEventListener('click', function () {
            window.print();
        });
    }

    window.addEventListener('load', function () {
        if (new URLSearchParams(window.location.search).get('print') === '1') {
            window.print();
        }
    });
})();
