document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('calendar-container');

    if (calendarEl) {
        const isTherapist = calendarEl.getAttribute('data-is-therapist') === 'true';

        // Зчитуємо базовий розмір шрифту браузера для динамічної конвертації px у rem
        const rootFontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);

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
            contentHeight: 'auto',
            expandRows: true,
            selectable: isTherapist,
            events: '/api/sessions',

            // 🟢 Відмова від eventDidMount з інлайновими стилями на користь класів
            eventClassNames: function() {
                return ['mmc-calendar-event'];
            },

            dateClick: function(info) {
                if (isTherapist) {
                    openSessionModal(info.dateStr);
                }
            },

            eventMouseEnter: function(info) {
                            const tooltip = document.createElement('div');
                            tooltip.className = 'mmc-event-tooltip';

                            // 1. Форматуємо час безпечно через нативний API браузера
                            const timeOptions = { hour: '2-digit', minute: '2-digit' };
                            const startTime = info.event.start ? info.event.start.toLocaleTimeString('uk-UA', timeOptions) : '';
                            const endTime = info.event.end ? info.event.end.toLocaleTimeString('uk-UA', timeOptions) : '';
                            const timeString = (startTime && endTime) ? `${startTime} - ${endTime}` : startTime;

                            // 2. Формуємо HTML без інлайнових стилів
                            tooltip.innerHTML = `
                                <div class="mmc-tooltip-time">🕒 ${timeString}</div>
                                <div class="mmc-tooltip-title">${info.event.title}</div>
                            `;

                            document.body.appendChild(tooltip);

                            // 3. Динамічний розрахунок координат у rem (Quantum grid)
                            const rootFontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
                            const rect = info.el.getBoundingClientRect();

                            const topInRem = (rect.top + window.scrollY) / rootFontSize;
                            const leftInRem = (rect.left + window.scrollX) / rootFontSize;

                            tooltip.style.setProperty('--tooltip-top', `${topInRem}rem`);
                            tooltip.style.setProperty('--tooltip-left', `${leftInRem}rem`);

                            info.el._tooltip = tooltip;
                        },

            eventMouseLeave: function(info) {
                // Видаляємо вікно з пам'яті та DOM, коли курсор прибрано
                if (info.el._tooltip) {
                    info.el._tooltip.remove();
                    info.el._tooltip = null;
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