let currentCriticalNotificationId = null;

// Фонове опитування бекенду (раз на 12 секунд)
function pollNotifications() {
    fetch('/api/notifications/summary')
        .then(response => {
            if (!response.ok) return null;
            return response.json();
        })
        .then(summary => {
            if (!summary) return;

            updateBellBadge(summary.unreadCount);

            // Перевірка на критичні сповіщення
            if (summary.criticalAdminAlert) {
                showCriticalModal(summary.criticalAdminAlert, '⚠️ Червоне повідомлення від Адміністрації');
            } else if (summary.criticalTherapyCall) {
                showCriticalModal(summary.criticalTherapyCall, '🩺 Запрошення на терапевтичну сесію');
            }
        })
        .catch(err => console.debug('[Notifications] Опитування активне...'));
}

// Оновлення лічильника на дзвіночку
function updateBellBadge(unreadCount) {
    const badge = document.getElementById('notificationBadge');
    const bellBtn = document.getElementById('notificationBell');

    if (!badge || !bellBtn) return;

    if (unreadCount > 0) {
        badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
        badge.classList.remove('is-hidden');
        bellBtn.classList.add('has-unread');
    } else {
        badge.classList.add('is-hidden');
        bellBtn.classList.remove('has-unread');
    }
}

// Відображення домінуючої критичної модалки
function showCriticalModal(item, defaultTitle) {
    const modal = document.getElementById('criticalAlertModal');
    const titleEl = document.getElementById('criticalAlertTitle');
    const messageEl = document.getElementById('criticalAlertMessage');
    const linkEl = document.getElementById('criticalAlertLink');

    if (!modal) return;

    currentCriticalNotificationId = item.id;
    titleEl.textContent = item.title || defaultTitle;
    messageEl.textContent = item.message;

    if (item.targetUrl) {
        linkEl.href = item.targetUrl;
        linkEl.classList.remove('is-hidden');
    } else {
        linkEl.classList.add('is-hidden');
    }

    modal.classList.remove('is-hidden');
}

// Закриття критичної модалки з повагою до прочитання
function closeCriticalModal() {
    const modal = document.getElementById('criticalAlertModal');
    if (modal) {
        modal.classList.add('is-hidden');
    }

    if (currentCriticalNotificationId) {
        const csrfInput = document.querySelector('input[name="_csrf"]');
        const csrfToken = csrfInput ? csrfInput.value : '';

        fetch(`/api/notifications/${currentCriticalNotificationId}/read`, {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': csrfToken }
        });
    }
}

// Перемикач випадаючого списку
function toggleNotificationDropdown() {
    const dropdown = document.getElementById('notificationDropdown');
    if (!dropdown) return;

    const isHidden = dropdown.classList.toggle('is-hidden');
    if (!isHidden) {
        loadNotificationsFeed();
    }
}

// Завантаження списку сповіщень при відкритті
function loadNotificationsFeed() {
    const listContainer = document.getElementById('notificationList');
    if (!listContainer) return;

    fetch('/api/notifications/feed?page=0&size=10')
        .then(res => res.json())
        .then(slice => {
            if (!slice.content || slice.content.length === 0) {
                listContainer.innerHTML = '<p class="text-muted text-sm p-xs text-center">Немає сповіщень</p>';
                return;
            }

            listContainer.innerHTML = slice.content.map(item => `
                <div class="p-xs border-bottom ${item.isRead ? 'text-muted' : 'font-bold'}">
                    <small class="text-muted">${item.title}</small>
                    <p class="text-sm mb-0">${item.message}</p>
                </div>
            `).join('');
        });
}

// Запуск фонового опитування при завантаженні сторінки
document.addEventListener('DOMContentLoaded', () => {
    pollNotifications();
    setInterval(pollNotifications, 12000); // Опитування кожні 12 секунд
});