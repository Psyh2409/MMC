// 1. Глобальна функція для перемикання вкладок у профілі
window.switchPublicTab = function(tabId, clickedBtn) {
    // Ховаємо всі вкладки у головній секції профілю
    document.querySelectorAll('.profile-main-content .tab-pane, .tab-pane').forEach(tab => {
        tab.classList.add('is-hidden');
        tab.classList.remove('active');
    });

    // Знімаємо активний стан з усіх кнопок навігації
    document.querySelectorAll('.tabs-navigation .tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Показуємо цільову вкладку
    const targetTab = document.getElementById(tabId);
    if (targetTab) {
        targetTab.classList.remove('is-hidden');
        targetTab.classList.add('active');
    }

    // Підсвічуємо відповідну кнопку
    if (clickedBtn) {
        clickedBtn.classList.add('active');
    }
};

// 2. Глобальна функція асинхронної пагінації активностей (AJAX)
window.loadActivityPage = function(page) {
    fetch('/profile/activity/feed?page=' + page)
        .then(response => response.text())
        .then(html => {
            const container = document.getElementById('activityFeedContainer');
            if (container) {
                container.innerHTML = html;
                const activityTab = document.getElementById('activity-tab');
                if (activityTab) {
                    activityTab.scrollIntoView({ behavior: 'smooth' });
                }
            }
        })
        .catch(error => console.error('Помилка завантаження активності:', error));
};

// 3. Автоматична активація вкладки за наявності якоря в URL (#activity-tab)
document.addEventListener('DOMContentLoaded', function() {
    const hash = window.location.hash;
    if (hash) {
        const tabId = hash.replace('#', '');
        const targetTab = document.getElementById(tabId);

        if (targetTab) {
            const matchingBtn = document.querySelector(`.tabs-navigation .tab-button[data-target="${tabId}"]`) ||
                                document.querySelector(`.tabs-navigation .tab-button[onclick*="'${tabId}'"]`);

            window.switchPublicTab(tabId, matchingBtn);
        }
    }
});