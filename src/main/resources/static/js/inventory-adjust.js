(function () {
    'use strict';

    var row = document.getElementById('direction-row');
    if (!row) {
        return;
    }
    document.querySelectorAll('input[name="type"]').forEach(function (radio) {
        radio.addEventListener('change', function () {
            row.classList.toggle('hidden', radio.value !== 'CORRECTION' || !radio.checked);
        });
        if (radio.checked) {
            row.classList.toggle('hidden', radio.value !== 'CORRECTION');
        }
    });
})();
