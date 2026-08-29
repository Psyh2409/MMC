document.addEventListener('DOMContentLoaded', function () {
    const postsContainer = document.getElementById('postsContainer');
    const trigger = document.getElementById('loadMoreTrigger');
    const backToTopBtn = document.getElementById('backToTopBtn');

    if (typeof window.applyMediaFacades === 'function') {
        window.applyMediaFacades();
    }

    // 1. Поява плаваючої кнопки "Нагору" при скролі вікна
    if (backToTopBtn) {
        window.addEventListener('scroll', function () {
            if (window.scrollY > 300) {
                backToTopBtn.classList.add('visible');
            } else {
                backToTopBtn.classList.remove('visible');
            }
        });

        backToTopBtn.addEventListener('click', function () {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    if (!postsContainer || !trigger) return;

    let currentPage = 1;
    let isLoading = false;
    let hasMore = trigger.getAttribute('data-has-more') === 'true';

    // 2. Безпечне підвантаження постів при прокрутці сторінки
    const observer = new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting && !isLoading && hasMore) {
            loadNextBatch();
        }
    }, { threshold: 0.1 });

    observer.observe(trigger);

    function loadNextBatch() {
        isLoading = true;
        fetch('/api/public/posts?page=' + currentPage)
            .then(function (response) {
                if (response.redirected) {
                    throw new Error('Отримано редирект авторизації');
                }
                return response.text();
            })
            .then(function (html) {
                if (html.includes('<!DOCTYPE') || html.includes('<html')) {
                    hasMore = false;
                    trigger.style.display = 'none';
                    return;
                }

                if (html.trim().length > 0) {
                    postsContainer.insertAdjacentHTML('beforeend', html);
                    currentPage++;

                    if (typeof window.applyMediaFacades === 'function') {
                        window.applyMediaFacades();
                    }
                } else {
                    hasMore = false;
                    trigger.style.display = 'none';
                }
            })
            .catch(function (err) {
                console.error('Помилка підвантаження постів:', err);
                hasMore = false;
                trigger.style.display = 'none';
            })
            .finally(function () {
                isLoading = false;
            });
    }

});

// 1. Локальна прокрутка (Стрічка постів)
const feedWindow = document.getElementById('postsFeedWindow');
const feedBtn = document.getElementById('feedBackToTopBtn');

if (feedWindow && feedBtn) {
    feedWindow.addEventListener('scroll', function () {
        feedBtn.classList.toggle('visible', feedWindow.scrollTop > 150);
    });
    feedBtn.addEventListener('click', function () {
        feedWindow.scrollTo({ top: 0, behavior: 'smooth' });
    });
}

// 2. Глобальна прокрутка (Весь сайт)
const globalBtn = document.getElementById('backToTopBtn');

if (globalBtn) {
    window.addEventListener('scroll', function () {
        globalBtn.classList.toggle('visible', window.scrollY > 300);
    });
    globalBtn.addEventListener('click', function () {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
}