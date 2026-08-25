// 🟢 Формуємо час без переходу в UTC (зберігаємо український час)
const toLocalIsoString = (date) => {
    if (!date) return '';
    const pad = (n) => (n < 10 ? '0' + n : n);
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
};

// 🟢 Утиліта для примусового очищення всіх "завислих" тултипів
const clearAllTooltips = () => {
    document.querySelectorAll('.mmc-event-tooltip').forEach(el => el.remove());
};

document.addEventListener('DOMContentLoaded', function () {
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
            // 🟢 Дозволяємо перетягування подій для фахівця
            editable: isTherapist,
            eventDurationEditable: false, // Забороняємо розтягувати подію, лише переносити

            // 🟢 Динамічно вішаємо клас, якщо подія скасована
            eventClassNames: function (info) {
                const classes = ['mmc-calendar-event'];
                if (info.event.extendedProps.status === 'CANCELLED') {
                    classes.push('mmc-event-cancelled');
                }
                return classes;
            },

            // 🟢 Вбиваємо тултипи, як тільки починається перетягування
            eventDragStart: function (info) {
                clearAllTooltips(); // 🟢 Вбиваємо тултип при початку перетягування
            },

            // 🟢 Оновлена обробка перетягування (Drag-and-Drop)
            eventDrop: function (info) {
                // Прибираємо тултипи ще раз для надійності
                clearAllTooltips(); // 🟢 Вбиваємо тултип при початку перетягування

                if (!confirm(`Перенести сесію на ${info.event.start.toLocaleString('uk-UA')}?`)) {
                    info.revert();
                    return;
                }

                const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
                const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

                const formData = new URLSearchParams();
                // 🟢 Використовуємо нашу нову функцію замість toISOString()
                formData.append('newStart', toLocalIsoString(info.event.start));
                formData.append('newEnd', toLocalIsoString(info.event.end));

                fetch(`/api/sessions/${info.event.id}/reschedule`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        [csrfHeaderMeta.getAttribute('content')]: csrfTokenMeta.getAttribute('content')
                    },
                    body: formData
                })
                    .then(async response => {
                        if (!response.ok) {
                            const errorMsg = await response.text();
                            alert('Помилка перенесення: ' + errorMsg);
                            info.revert();
                        }
                    })
                    .catch(error => {
                        console.error('Помилка:', error);
                        info.revert();
                    });
            },

            // 🟢 Відмова від eventDidMount з інлайновими стилями на користь класів
            eventClassNames: function () {
                return ['mmc-calendar-event'];
            },

            dateClick: function (info) {
                if (isTherapist) {
                    openSessionModal(info.dateStr);
                }
            },

            eventMouseEnter: function (info) {
                const tooltip = document.createElement('div');
                tooltip.className = 'mmc-event-tooltip';

                // Форматуємо час
                const timeOptions = { hour: '2-digit', minute: '2-digit' };
                const startTime = info.event.start ? info.event.start.toLocaleTimeString('uk-UA', timeOptions) : '';
                const endTime = info.event.end ? info.event.end.toLocaleTimeString('uk-UA', timeOptions) : '';
                const timeString = (startTime && endTime) ? `${startTime} - ${endTime}` : startTime;

                // Дістаємо опис з extendedProps
                const description = info.event.extendedProps.description;

                // Формуємо HTML. Якщо опис є, додаємо відповідний блок.
                let tooltipHtml = `
                                <div class="mmc-tooltip-time">🕒 ${timeString}</div>
                                <div class="mmc-tooltip-title">${info.event.title}</div>
                            `;

                if (description) {
                    tooltipHtml += `<div class="mmc-tooltip-desc">${description}</div>`;
                }

                tooltip.innerHTML = tooltipHtml;

                document.body.appendChild(tooltip);

                // Динамічний розрахунок координат у rem
                const rootFontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
                const rect = info.el.getBoundingClientRect();

                const topInRem = (rect.top + window.scrollY) / rootFontSize;
                const leftInRem = (rect.left + window.scrollX) / rootFontSize;

                tooltip.style.setProperty('--tooltip-top', `${topInRem}rem`);
                tooltip.style.setProperty('--tooltip-left', `${leftInRem}rem`);

                info.el._tooltip = tooltip;
            },

            eventClick: function (info) {
                if (isTherapist) {
                    clearAllTooltips(); // 🟢 Вбиваємо тултип перед відкриттям модалки
                    openSessionViewModal(info.event);
                }
            },

            eventMouseLeave: function (info) {
                // Видаляємо вікно з пам'яті та DOM, коли курсор прибрано
                if (info.el._tooltip) {
                    info.el._tooltip.remove();
                    info.el._tooltip = null;
                }
            }
        });

        calendar.render();
        // 🟢 Зберігаємо посилання на календар глобально, щоб могти змінювати події з інших функцій
        window.mmcCalendar = calendar;
    }
});

// Керування модальним вікном (працюватиме лише якщо форма є в HTML)
window.toggleSessionModal = function () {
    const modal = document.getElementById('session-modal');
    if (modal) {
        modal.classList.toggle('is-visible');
    }
};

window.openSessionModal = function (dateStr) {
    const startInput = document.getElementById('session-start');
    if (startInput && dateStr) {
        startInput.value = dateStr.substring(0, 16);
    }
    window.toggleSessionModal();
};

window.saveSession = function () {
    const clientId = document.getElementById('session-client-id').value;
    const startTimeStr = document.getElementById('session-start').value;
    // 🟢 Зчитуємо коментар
    const description = document.getElementById('session-description').value.trim();
    const recurringWeeks = document.getElementById('session-recurring').value; // 🟢 Зчитуємо тижні

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
    formData.append('recurringWeeks', recurringWeeks); // 🟢 Передаємо на сервер

    // 🟢 Додаємо коментар, якщо він не порожній
    if (description) {
        formData.append('description', description);
    }

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

window.toggleSessionViewModal = function () {
    const modal = document.getElementById('session-view-modal');
    if (modal) {
        modal.classList.toggle('is-visible');
    }
};

window.openSessionViewModal = function (event) {
    // Якщо сесія вже скасована, можемо блокувати відкриття або показувати відповідний статус
    if (event.backgroundColor === 'var(--text-disabled)') {
        alert('Ця сесія вже скасована.');
        return;
    }

    document.getElementById('view-session-id').value = event.id;
    document.getElementById('view-session-title').textContent = event.title;

    // Форматуємо час для відображення
    const timeOpts = { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' };
    const startStr = event.start ? event.start.toLocaleString('uk-UA', timeOpts) : '';
    const endStr = event.end ? event.end.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' }) : '';
    document.getElementById('view-session-time').textContent = `${startStr} - ${endStr}`;

    // Керування видимістю коментаря без інлайнових стилів (використовуємо клас is-hidden)
    const descContainer = document.getElementById('view-session-desc-container');
    const descText = document.getElementById('view-session-desc');

    if (event.extendedProps.description) {
        descText.textContent = event.extendedProps.description;
        descContainer.classList.remove('is-hidden');
    } else {
        descContainer.classList.add('is-hidden');
        descText.textContent = '';
    }

    window.toggleSessionViewModal();
};

window.cancelExistingSession = function () {
    const sessionId = document.getElementById('view-session-id').value;
    const reasonElement = document.getElementById('cancel-reason');
    const reason = reasonElement ? reasonElement.value.trim() : '';

    if (!sessionId) return;
    if (!confirm('Ви впевнені, що хочете скасувати цю сесію? Відновити її буде неможливо.')) return;

    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    const url = `/api/sessions/${sessionId}/cancel` + (reason ? `?reason=${encodeURIComponent(reason)}` : '');

    fetch(url, {
        method: 'PATCH',
        headers: {
            [csrfHeader]: csrfToken
        }
    })
        .then(async response => {
            if (response.ok) {
                window.toggleSessionViewModal();
                clearAllTooltips(); // 🟢 Контрольна зачистка пам'яті

                // 🟢 Миттєво ВИДАЛЯЄМО подію з DOM, звільняючи місце
                if (window.mmcCalendar) {
                    const event = window.mmcCalendar.getEventById(sessionId);
                    if (event) {
                        event.remove();
                    }
                }
            } else {
                const errorMsg = await response.text();
                alert('Помилка: ' + errorMsg);
            }
        })
        .catch(error => console.error('[MMC UI Error] Помилка скасування:', error));
};