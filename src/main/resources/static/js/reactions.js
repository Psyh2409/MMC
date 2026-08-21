// reactions.js

// Відкрити/сховати панель реакцій (горизонтальне розгортання)
function toggleReactionDropdown(btn) {
    // Ховаємо всі інші відкриті панелі на сторінці
    document.querySelectorAll('.reaction-dropdown').forEach(drop => {
        if (drop !== btn.nextElementSibling) drop.classList.add('is-hidden');
    });

    const dropdown = btn.nextElementSibling;
    if (dropdown) {
        dropdown.classList.toggle('is-hidden');
    }
}

// Застосувати або скасувати обрану реакцію (при кліку)
function applyReaction(btn) {
    const dropdown = btn.closest('.reaction-dropdown');
    const wrapper = btn.closest('.reaction-wrapper');
    const mainBtn = wrapper.querySelector('.reaction-toggle-btn');
    const countSpan = mainBtn.querySelector('.reaction-count');
    const iconContainer = mainBtn.querySelector('.reaction-current-icon');

    const targetType = mainBtn.getAttribute('data-target-type');
    const targetId = mainBtn.getAttribute('data-target-id');
    const clickedReaction = btn.getAttribute('data-reaction');
    const currentReaction = mainBtn.getAttribute('data-current-reaction');

    const newIconHtml = btn.innerHTML;
    // Визначаємо, чи користувач хоче зняти свій лайк
    const isRemoving = (currentReaction === clickedReaction);

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

    fetch('/api/reactions/toggle', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({ targetType, targetId, reactionType: clickedReaction })
    })
    .then(response => {
        if (!response.ok) throw new Error('Помилка сервера');

        let currentCount = parseInt(countSpan.innerText) || 0;

        if (isRemoving) {
            // ЛОГІКА СКАСУВАННЯ
            mainBtn.classList.remove('border-accent');
            mainBtn.removeAttribute('data-current-reaction');
            countSpan.innerText = Math.max(0, currentCount - 1);
            iconContainer.innerHTML = '🤍'; // Повертаємо дефолтну іконку
        } else {
            // ЛОГІКА ДОДАВАННЯ АБО ЗМІНИ
            if (!currentReaction) {
                // Збільшуємо лічильник тільки якщо раніше не було жодної емоції
                countSpan.innerText = currentCount + 1;
            }
            mainBtn.classList.add('border-accent');
            mainBtn.setAttribute('data-current-reaction', clickedReaction);
            iconContainer.innerHTML = newIconHtml;
        }

        dropdown.classList.add('is-hidden');
    })
    .catch(error => console.error('Помилка збереження реакції:', error));
}

// Закриття при кліку повз панель
document.addEventListener('click', (e) => {
    if (!e.target.closest('.reaction-wrapper')) {
        document.querySelectorAll('.reaction-dropdown').forEach(drop => {
            drop.classList.add('is-hidden');
        });
    }
});

// Завантаження актуальних станів реакцій з сервера
function loadReactions() {
    const reactionBtns = document.querySelectorAll('.reaction-toggle-btn');
    if (reactionBtns.length === 0) return;

    // Збираємо всі унікальні ID постів/коментарів зі сторінки
    const targetIds = Array.from(reactionBtns).map(btn => btn.getAttribute('data-target-id'));
    const uniqueIds = [...new Set(targetIds)];

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

    fetch('/api/reactions/summaries', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(uniqueIds)
        })
        .then(res => {
            if (!res.ok) {
                throw new Error('Помилка сервера: ' + res.status);
            }
            // Захист: перевіряємо, чи сервер повернув саме JSON, а не HTML-сторінку
            const contentType = res.headers.get("content-type");
            if (!contentType || !contentType.includes("application/json")) {
                throw new TypeError("Отримано не JSON. Можливо, спрацював редірект Spring Security.");
            }
            return res.json();
        })
        .then(data => {
            reactionBtns.forEach(btn => {
                const targetId = btn.getAttribute('data-target-id');
                const summary = data[targetId];
                if (summary) updateReactionUI(btn, summary);
            });
        })
        .catch(error => console.warn('[Reactions] Завантаження призупинено:', error.message));
}

// Застосування стилів та лічильників з БД до кнопки (при завантаженні)
function updateReactionUI(mainBtn, summary) {
    const countSpan = mainBtn.querySelector('.reaction-count');
    const iconContainer = mainBtn.querySelector('.reaction-current-icon');

    const total = Object.values(summary.counts).reduce((a, b) => a + b, 0);
    countSpan.innerText = total;

    if (summary.userReaction) {
        mainBtn.classList.add('border-accent');
        // Запам'ятовуємо поточну емоцію в data-атрибут
        mainBtn.setAttribute('data-current-reaction', summary.userReaction);

        const dropdown = mainBtn.nextElementSibling;
        if (dropdown) {
            const selectedBtn = dropdown.querySelector(`[data-reaction="${summary.userReaction}"]`);
            if (selectedBtn) {
                iconContainer.innerHTML = selectedBtn.innerHTML;
            }
        }
    } else {
        mainBtn.removeAttribute('data-current-reaction');
    }
}

// Автоматичний запуск при відкритті звичайних сторінок (статті, стіни)
document.addEventListener('DOMContentLoaded', loadReactions);

// Автоматичний скрол до якоря після AJAX-завантаження контенту
function scrollToHashAfterRender() {
    if (window.location.hash) {
        const targetElement = document.querySelector(window.location.hash);
        if (targetElement) {
            setTimeout(() => {
                targetElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                targetElement.classList.add('pulse-highlight'); // Можна додати візуальний акцент, якщо є такий клас
            }, 300);
        }
    }
}