// Глобальна функція для перемикання вкладок
window.switchPublicTab = function(tabId, clickedBtn) {
    // Ховаємо всі вкладки
    document.querySelectorAll('.profile-card .tab-pane').forEach(tab => {
        tab.classList.add('is-hidden');
        tab.classList.remove('active');
    });

    // Знімаємо підсвітку з усіх кнопок
    document.querySelectorAll('.tabs-navigation .tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Показуємо потрібну вкладку
    const targetTab = document.getElementById(tabId);
    if (targetTab) {
        targetTab.classList.remove('is-hidden');
        targetTab.classList.add('active');
    }
    clickedBtn.classList.add('active');
};

// Захист від подвійного кліку при запиті до фахівця
document.addEventListener('DOMContentLoaded', function() {
    const actionForm = document.querySelector('.action-group form');
    if (actionForm) {
        actionForm.addEventListener('submit', function(e) {
            let btn = this.querySelector('button');
            if (btn) {
                btn.disabled = true;
                btn.innerText = 'Відправка...';
            }
        });
    }
});

/**
 * Завантаження сторінки активності користувача через AJAX
 * @param {number} page - номер сторінки для завантаження
 */
window.loadActivityPage = function(page) {
    fetch('/profile/activity/feed?page=' + page)
        .then(response => response.text())
        .then(html => {
            const container = document.getElementById('activityFeedContainer');
            if (container) {
                container.innerHTML = html;
                document.getElementById('activity-tab').scrollIntoView({ behavior: 'smooth' });
            }
        })
        .catch(error => console.error('Помилка завантаження активності:', error));
};