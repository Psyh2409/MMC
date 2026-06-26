function filterTable(role) {
    // Змінено селектор: шукаємо тільки основні рядки користувачів (ігноруємо рядки з дипломами)
    const rows = document.querySelectorAll('table tbody tr.user-row');

    rows.forEach(row => {
        const rowRole = row.getAttribute('data-role');
        if (role === 'ALL' || rowRole === role) {
            row.style.display = '';
        } else {
            row.style.display = 'none';

            // Якщо ми ховаємо юзера через фільтр, примусово ховаємо і його відкриту заявку
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