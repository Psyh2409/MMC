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

document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('calendar-container');

    if (calendarEl) {
        const calendar = new FullCalendar.Calendar(calendarEl, {
            initialView: 'timeGridWeek', // Вигляд за замовчуванням (тиждень з годинами)
            locale: 'uk', // Українська мова
            headerToolbar: {
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,timeGridDay'
            },
            slotMinTime: '08:00:00', // Робочі години (візуальне обмеження)
            slotMaxTime: '22:00:00',
            allDaySlot: false,
            height: 'auto', // Календар підлаштується під свій контент, а CSS max-height його стримує
            expandRows: true, // Дозволяє рядкам стискатися для компактності
            selectable: true,
            // Підключаємо наш REST API. FullCalendar сам додасть ?start=...&end=...
            events: '/api/sessions',

            // Обробник виділення діапазону часу (для створення сесії)
            select: function(info) {
                console.log('[MMC Calendar] Select event triggered:', info);
                openSessionModal(info.startStr);
                calendar.unselect(); // Скидаємо виділення після відкриття модалки
            },

            // Обробник кліку по існуючій події
            eventClick: function(info) {
                console.log('Клік по події:', info.event);
                // Тут ми будемо відкривати модалку для редагування/скасування
            }
        });

        calendar.render();
    }
});

// Керування модальним вікном "на пальцях"
window.toggleSessionModal = function() {
    console.log('[MMC Calendar] toggleSessionModal called');
    const modal = document.getElementById('session-modal');
    if (modal) {
        // Перевіряємо поточний стан
        const isHidden = modal.classList.contains('is-hidden');
        console.log('[MMC Calendar] Current is-hidden state:', isHidden);

        if (isHidden) {
            modal.classList.remove('is-hidden');
            console.log('[MMC Calendar] Removed is-hidden. New classes:', modal.className);
        } else {
            modal.classList.add('is-hidden');
            console.log('[MMC Calendar] Added is-hidden. New classes:', modal.className);
        }

        // Перевіряємо computed styles
        const computedStyle = window.getComputedStyle(modal);
        console.log('[MMC Calendar] Computed display:', computedStyle.display);
        console.log('[MMC Calendar] Computed visibility:', computedStyle.visibility);
    } else {
        console.error('[MMC Calendar] session-modal element not found!');
    }
};

window.openSessionModal = function(dateStr) {
    console.log('[MMC Calendar] openSessionModal called with dateStr:', dateStr);

    // Вставляємо обрану в календарі дату у поле форми
    const startInput = document.getElementById('session-start');
    if (startInput) {
        // Форматуємо дату для <input type="datetime-local">
        // info.dateStr містить зміщення часового поясу, обрізаємо його
        startInput.value = dateStr.substring(0, 16);
        console.log('[MMC Calendar] Date set to input:', startInput.value);
    } else {
        console.error('[MMC Calendar] session-start input not found!');
    }

    toggleSessionModal();
};

window.saveSession = function() {
    const clientId = document.getElementById('session-client-id').value;
    const startTimeStr = document.getElementById('session-start').value;

    if (!clientId || !startTimeStr) {
        alert('Будь ласка, оберіть клієнта та час.');
        return;
    }

    // Spring очікує повний формат ISO, тому якщо браузер не додав секунди - додаємо їх вручну
    const startTime = startTimeStr.length === 16 ? startTimeStr + ':00' : startTimeStr;

    // Зчитуємо CSRF токен для безпечного POST-запиту
    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    // Формуємо дані для відправки
    const formData = new URLSearchParams();
    formData.append('clientId', clientId);
    formData.append('startTime', startTime);

    fetch('/api/sessions', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            [csrfHeader]: csrfToken
        },
        body: formData
    })
    .then(response => {
        if (response.ok) {
            toggleSessionModal();
            // Найпростіший спосіб оновити дані на екрані - перезавантажити сторінку
            window.location.reload();
        } else {
            alert('Помилка при збереженні сесії.');
        }
    })
    .catch(error => console.error('[MMC UI Error] Помилка збереження:', error));
};