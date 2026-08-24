document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('calendar-container');

    if (calendarEl) {
        // Зчитуємо прапорець, який ми передали з контролера
        const isTherapist = calendarEl.getAttribute('data-is-therapist') === 'true';

        const calendar = new FullCalendar.Calendar(calendarEl, {
            initialView: 'timeGridWeek',
            locale: 'uk',
            headerToolbar: {
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,timeGridDay'
            },
            slotMinTime: '08:00:00',
            slotMaxTime: '22:00:00',
            allDaySlot: false,
            contentHeight: 'auto', //🟢 вимикає внутрішню прокрутку FullCalendar
            expandRows: true,

            // Якщо фахівець - дозволяємо виділення та кліки. Якщо клієнт - режим читання.
            selectable: isTherapist,

            events: '/api/sessions',

            dateClick: function(info) {
                if (isTherapist) {
                    openSessionModal(info.dateStr);
                }
            },

            eventClick: function(info) {
                console.log('Клік по події:', info.event);
                // Тут згодом буде логіка перегляду/редагування події
            },

            eventDidMount: function(info) {
                const eventEl = info.el;
                if (eventEl) {
                    eventEl.style.backgroundColor = 'var(--shadow-main)';
                    eventEl.style.borderColor = 'var(--shadow-main)';
                }
            }
        });

        calendar.render();
    }
});

// Керування модальним вікном (працюватиме лише якщо форма є в HTML)
window.toggleSessionModal = function() {
    const modal = document.getElementById('session-modal');
    if (modal) {
        modal.classList.toggle('is-visible');
    }
};

window.openSessionModal = function(dateStr) {
    const startInput = document.getElementById('session-start');
    if (startInput && dateStr) {
        startInput.value = dateStr.substring(0, 16);
    }
    window.toggleSessionModal();
};

window.saveSession = function() {
    const clientId = document.getElementById('session-client-id').value;
    const startTimeStr = document.getElementById('session-start').value;

    if (!clientId || !startTimeStr) {
        alert('Будь ласка, оберіть клієнта та час.');
        return;
    }

    const startTime = startTimeStr.length === 16 ? startTimeStr + ':00' : startTimeStr;

    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

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
    .then(async response => {
            if (response.ok) {
                window.toggleSessionModal();
                window.location.reload();
            } else {
                // Читаємо текст помилки з бекенду (наприклад: "У вас або у клієнта вже є...")
                const errorMsg = await response.text();
                alert('Помилка: ' + errorMsg);
            }
    })
    .catch(error => console.error('[MMC UI Error] Помилка збереження:', error));
};