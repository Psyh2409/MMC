function filterTable(role) {
    // Шукаємо ТІЛЬКИ ті рядки, де є атрибут data-role
    const rows = document.querySelectorAll('table tbody tr[data-role]');

    rows.forEach(row => {
        const rowRole = row.getAttribute('data-role');
        if (role === 'ALL' || rowRole === role) {
            row.style.display = '';
        } else {
            row.style.display = 'none';

            // Якщо ми приховуємо користувача, ховаємо і його блок з дипломом
            const targetId = row.getAttribute('data-target');
            if (targetId) {
                const appRow = document.getElementById(targetId);
                if (appRow && !appRow.classList.contains('is-hidden')) {
                    appRow.classList.add('is-hidden');
                }
            }
        }
    });
}

function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        alert("ID скопійовано: " + text);
    }).catch(err => {
        console.error('Помилка копіювання: ', err);
    });
}

function toggleApp(element) {
    // Спочатку перевіряємо data-target на самій кнопці
    let targetId = element.getAttribute('data-target');

    // Якщо на кнопці немає data-target, беремо з батьківського рядка
    if (!targetId) {
        const row = element.closest('tr');
        targetId = row.getAttribute('data-target');
    }

    if (!targetId) return;

    const appRow = document.getElementById(targetId);
    if (appRow) {
        appRow.classList.toggle('is-hidden');
    }
}

function openBanModal(buttonElement, title) {
    event.stopPropagation(); // Щоб не розгорталась форма повідомлення
    const modal = document.getElementById('banReasonModal');
    const form = document.getElementById('banReasonForm');
    const titleEl = document.getElementById('banModalTitle');

    // Встановлюємо URL з кнопки прямо у форму
    form.action = buttonElement.getAttribute('data-action');
    titleEl.textContent = title;

    modal.classList.remove('is-hidden');
}

function closeBanModal() {
    const modal = document.getElementById('banReasonModal');
    modal.classList.add('is-hidden');
    document.getElementById('banReasonForm').reset();
}

document.addEventListener('DOMContentLoaded', function() {
    checkAdminAlerts();
});

function checkAdminAlerts() {
    const badge = document.getElementById('footerAdminBadge');
    // Якщо бейджа немає на сторінці (користувач не адмін), перериваємо виконання
    if (!badge) return;

    fetch('/api/admin/alerts-count')
        .then(response => {
            if (!response.ok) throw new Error('Помилка авторизації або сервера');
            return response.json();
        })
        .then(count => {
            if (count > 0) {
                badge.textContent = count;
                badge.classList.remove('is-hidden');
            } else {
                badge.classList.add('is-hidden');
            }
        })
        .catch(error => {
            // Тихо ігноруємо помилки мережі, щоб не засмічувати консоль
            console.debug('[MMC Admin] Не вдалося завантажити лічильник:', error);
        });
}

let currentSosRequestId = null;

function openAdminSosModal(btnElement) {
    const requestId = btnElement.getAttribute('data-id');
    const reason = btnElement.getAttribute('data-reason');
    const assignmentId = btnElement.getAttribute('data-assignment');

    currentSosRequestId = requestId;

    const reasonContainer = document.getElementById('sos-modal-reason');
    const roomLink = document.getElementById('sos-modal-room-link');
    const modal = document.getElementById('admin-sos-modal');

    if (reasonContainer && roomLink && modal) {
        reasonContainer.textContent = reason;
        roomLink.href = '/therapy/room/' + assignmentId;

        modal.classList.remove('hidden');
        modal.classList.add('is-visible');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('admin-sos-modal');
    const btnResolve = document.getElementById('btn-resolve-sos');
    const btnClose = document.getElementById('btn-close-sos-modal');

    // Кнопка "Повернутись назад" (просто закриває модалку, запис залишається активним)
    if (btnClose && modal) {
        btnClose.addEventListener('click', () => {
            modal.classList.remove('is-visible');
            modal.classList.add('hidden');
        });
    }

    // Кнопка "Позначити як вирішено"
    if (btnResolve) {
        btnResolve.addEventListener('click', async () => {
            if (!currentSosRequestId) return;

            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

            try {
                const response = await fetch('/admin/sos/' + currentSosRequestId + '/resolve', {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken }
                });

                if (response.ok) {
                    window.location.reload();
                } else {
                    alert('Помилка при закритті SOS-запиту.');
                }
            } catch (error) {
                console.error('Помилка мережі:', error);
            }
        });
    }
});