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

// 3. Автоматична активація вкладок та обробка форм при завантаженні сторінки
document.addEventListener('DOMContentLoaded', function() {
    // Активація вкладки за наявності якоря в URL (#activity-tab)
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

    // 4. МИТТЄВЕ АСИНХРОННЕ ЗБЕРЕЖЕННЯ НАЛАШТУВАНЬ EMAIL-СПОВІЩЕНЬ (AJAX)
    const emailCheckbox = document.getElementById('emailNotificationsCheckbox');

    if (emailCheckbox) {
        emailCheckbox.addEventListener('change', function () {
            const isChecked = this.checked;

            // Зчитуємо мета-теги Spring Security CSRF для захисту
            const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
            const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');

            const csrfToken = csrfTokenMeta ? csrfTokenMeta.getAttribute('content') : '';
            const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

            // Надсилаємо фоновий POST-запит на бекенд
            fetch('/api/profile/email-notifications', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeader]: csrfToken
                },
                body: new URLSearchParams({ enabled: isChecked })
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Помилка сервера при збереженні');
                }
            })
            .catch(err => {
                console.error('[Profile] Не вдалося зберегти налаштування сповіщень:', err);
                // Повертаємо стан чекбокса назад у разі мережевої помилки
                this.checked = !isChecked;
            });
        });
    }
});