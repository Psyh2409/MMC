// ==========================================================================
// МОДУЛЬ КЕРУВАННЯ РОЗГОРТАННЯМ РЯДКА СПОВІЩЕННЯ КЛІЄНТА
// ==========================================================================

// Явна прив'язка до об'єкта window робить функцію доступною для onclick у будь-якому контейнері
window.toggleClientMessageRow = function(btn) {
    if (!btn) return;

    const targetId = btn.getAttribute('data-target');
    const row = document.getElementById(targetId);

    if (row) {
        row.classList.toggle('is-hidden');
    } else {
        console.error('[MMC UI Error] Елемент рядка не знайдено з ID:', targetId);
    }
};