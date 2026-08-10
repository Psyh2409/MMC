let currentCriticalNotificationId = null;

// Фонове опитування бекенду (кожні 12 секунд)
function pollNotifications() {
    fetch('/api/notifications/summary')
        .then(response => {
            if (!response.ok) return null;
            return response.json();
        })
        .then(summary => {
            if (!summary) return;

            updateBellBadge(summary.unreadCount);

            if (summary.criticalAdminAlert) {
                showCriticalModal(summary.criticalAdminAlert, 'Важливе сповіщення від Адміністратора');
            } else if (summary.criticalTherapyCall) {
                showCriticalModal(summary.criticalTherapyCall, 'Запрошення на терапевтичну сесію');
            }
        })
        .catch(err => console.debug('[Notifications] Опитування активне...'));
}

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

// При відкритті списку одразу ховаємо бейдж
function toggleNotificationDropdown() {
    const dropdown = document.getElementById('notificationDropdown');
    const badge = document.getElementById('notificationBadge');
    const bellBtn = document.getElementById('notificationBell');

    if (!dropdown) return;

    const isHidden = dropdown.classList.toggle('is-hidden');
    if (!isHidden) {
        if (badge) badge.classList.add('is-hidden');
        if (bellBtn) bellBtn.classList.remove('has-unread');
        loadNotificationsFeed();
    }
}

// Обрізання тексту до перших 5 слів
function getSnippet(text) {
    if (!text) return '';
    const words = text.trim().split(/\s+/);
    if (words.length <= 5) return text;
    return words.slice(0, 5).join(' ') + '...';
}

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
                <a href="${item.targetUrl || '#'}"
                   class="notification-item-link ${item.isRead ? 'text-muted' : 'font-bold'}"
                   onclick="handleNotificationClick(event, '${item.id}', '${item.targetUrl}')">
                    <div class="text-xs text-muted mb-xs">${item.title}</div>
                    <div class="text-sm">${getSnippet(item.message)}</div>
                </a>
            `).join('');
        });
}

// Перехід по сповіщенню з маркуванням як прочитане
function handleNotificationClick(event, id, targetUrl) {
    event.preventDefault();
    const csrfToken = getCsrfToken();

    fetch(`/api/notifications/${id}/read`, {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': csrfToken }
    }).finally(() => {
        if (targetUrl && targetUrl !== 'null' && targetUrl !== '#') {
            window.location.href = targetUrl;
        } else {
            loadNotificationsFeed();
        }
    });
}

function showCriticalModal(item, defaultTitle) {
    const modal = document.getElementById('criticalAlertModal');
    const titleEl = document.getElementById('criticalAlertTitle');
    const messageEl = document.getElementById('criticalAlertMessage');
    const linkEl = document.getElementById('criticalAlertLink');

    if (!modal) return;

    currentCriticalNotificationId = item.id;
    if (titleEl) titleEl.textContent = item.title || defaultTitle;
    if (messageEl) messageEl.textContent = item.message;

    if (linkEl && item.targetUrl) {
        linkEl.onclick = (e) => {
            e.preventDefault();
            closeCriticalModalAndNavigate(item.targetUrl);
        };
        linkEl.classList.remove('is-hidden');
    }

    modal.classList.remove('is-hidden');
}

function closeCriticalModalAndNavigate(targetUrl) {
    closeCriticalModal();
    if (targetUrl) {
        window.location.href = targetUrl;
    }
}

function closeCriticalModal() {
    const modal = document.getElementById('criticalAlertModal');
    if (modal) {
        modal.classList.add('is-hidden');
    }

    if (currentCriticalNotificationId) {
        const csrfToken = getCsrfToken();
        fetch(`/api/notifications/${currentCriticalNotificationId}/read`, {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': csrfToken }
        });
        currentCriticalNotificationId = null;
    }
}

function getCsrfToken() {
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    return csrfMeta ? csrfMeta.getAttribute('content') : '';
}

// Автоматичне закриття вікна при кліку в будь-якій точці екрана поза сповіщеннями
document.addEventListener('click', (event) => {
    const wrapper = document.querySelector('.notification-wrapper');
    const dropdown = document.getElementById('notificationDropdown');

    if (dropdown && !dropdown.classList.contains('is-hidden')) {
        // Якщо клік відбувся поза блоком .notification-wrapper — ховаємо список
        if (wrapper && !wrapper.contains(event.target)) {
            dropdown.classList.add('is-hidden');
        }
    }
});

// Автоматичне закриття при відведенні курсора миші за межі випадаючого вікна
document.addEventListener('DOMContentLoaded', () => {
    const wrapper = document.querySelector('.notification-wrapper');
    const dropdown = document.getElementById('notificationDropdown');

    if (wrapper) {
        wrapper.addEventListener('mouseleave', () => {
            if (dropdown && !dropdown.classList.contains('is-hidden')) {
                dropdown.classList.add('is-hidden');
            }
        });
    }

    // Фонове опитування бекенду
    pollNotifications();
    setInterval(pollNotifications, 12000);
});