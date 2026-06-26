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
            const row = element.closest('tr');
            const targetId = row.getAttribute('data-target');
            if (!targetId) return;

            const appRow = document.getElementById(targetId);
            if (appRow) {
                appRow.classList.toggle('is-hidden');
            }
        }