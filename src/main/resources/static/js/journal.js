document.addEventListener('DOMContentLoaded', () => {

    /* =========================================
       1. СИСТЕМНІ ТЕМИ ТА МОНОХРОМ
       ========================================= */
    const slider = document.getElementById('theme-slider');
    const ptsrLink = document.getElementById('ptsr-link');
    const root = document.documentElement;

    const savedTheme = localStorage.getItem('app-theme') || 'brown';
    const savedHue = localStorage.getItem('custom-hue');

    if (savedTheme === 'custom' && savedHue) {
        slider.value = savedHue;
    } else {
        slider.value = 15;
    }

    const updateTheme = (hue, sat = '75%', themeName = 'custom') => {
        root.setAttribute('data-theme', themeName);
        root.style.setProperty('--theme-h', hue);
        root.style.setProperty('--theme-s', sat);
        localStorage.setItem('app-theme', themeName);
        localStorage.setItem('custom-hue', hue);
        localStorage.setItem('custom-sat', sat);
    };

    if (slider) {
        slider.addEventListener('input', (e) => updateTheme(e.target.value, '75%', 'custom'));
    }

    if (ptsrLink) {
        ptsrLink.addEventListener('click', (e) => {
            e.preventDefault();
            updateTheme(210, '5%', 'custom');
            if(slider) slider.value = 210;
        });
    }

    /* =========================================
       2. АСИНХРОННЕ ЗАВАНТАЖЕННЯ АВАТАРА
       ========================================= */
    const avatarContainer = document.getElementById('avatarContainer');
    const avatarFileInput = document.getElementById('avatarFileInput');
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

    if (avatarContainer && avatarFileInput) {
        avatarContainer.addEventListener('click', () => avatarFileInput.click());

        avatarFileInput.addEventListener('change', async function() {
            const file = avatarFileInput.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('avatar', file);

            // Використовуємо CSS клас замість style.opacity
            avatarContainer.classList.add('loading-state');

            try {
                const response = await fetch('/api/profile/avatar', {
                    method: 'POST',
                    headers: { 'X-CSRF-TOKEN': csrfToken },
                    body: formData
                });

                if (!response.ok) throw new Error('Помилка сервера');
                const data = await response.json();

                let img = document.getElementById('avatarImage');
                const letterSpan = document.getElementById('avatarLetter');

                if (!img) {
                    img = document.createElement('img');
                    img.id = 'avatarImage';
                    img.alt = 'User Avatar';
                    avatarContainer.insertBefore(img, avatarContainer.firstChild);
                }
                img.src = data.url;
                if (letterSpan) letterSpan.remove();
            } catch (error) {
                alert('Не вдалося оновити фото: ' + error.message);
            } finally {
                avatarContainer.classList.remove('loading-state');
            }
        });
    }

    /* =========================================
       3. УПРАВЛІННЯ ВКЛАДКАМИ ТА ОНОВЛЕННЯ СТРІЧКИ
       ========================================= */
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabPanes = document.querySelectorAll('.tab-pane');
    const journalTab = document.getElementById('journal-tab');
    let isJournalLoaded = false;

    // Стандартне перемикання кнопок
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetId = btn.getAttribute('data-target');
            tabButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            tabPanes.forEach(pane => {
                pane.classList.toggle('active', pane.id === targetId);
            });
        });
    });

    // Надійний тригер через MutationObserver (якщо клас active додається глобальним скриптом)
    if (journalTab) {
        const triggerJournalLoad = () => {
            if (!isJournalLoaded) {
                window.loadJournalFeed();
                isJournalLoaded = true;
            }
        };

        if (journalTab.classList.contains('active')) triggerJournalLoad();

        const observer = new MutationObserver((mutations) => {
            mutations.forEach(() => {
                if (journalTab.classList.contains('active')) triggerJournalLoad();
            });
        });
        observer.observe(journalTab, { attributes: true, attributeFilter: ['class'] });
    }

    /* =========================================
       4. ВІДНОВЛЕНІ БЛОКИ (З ТВОГО СТАРОГО ФАЙЛУ)
       ========================================= */

    // --- 5. БАР'ЄР ДЕАКТИВАЦІЇ ---
    const toggleBtn = document.getElementById('toggleDeactivationBtn');
    const vault = document.getElementById('deactivationVault');
    if (toggleBtn && vault) {
        toggleBtn.addEventListener('click', () => vault.classList.toggle('active'));
    }

    // --- 6. ПЕРЕМИКАЧ ПАРОЛЯ ---
    document.querySelectorAll('.btn-toggle-password').forEach(btn => {
        btn.addEventListener('click', function() {
            const input = this.previousElementSibling;
            input.type = input.type === 'password' ? 'text' : 'password';
            this.textContent = input.type === 'password' ? '👁️' : '🔒';
        });
    });

    /* =========================================
       5. ДИНАМІКА ЩОДЕННИКА (ДЕЛЕГУВАННЯ ПОДІЙ)
       ========================================= */

    // А) Перехоплення подій SUBMIT для всіх форм (Запобігає 403 та 400)
        document.addEventListener('submit', async (e) => {
            const form = e.target;

            // --- Обробка базової форми створення ---
            if (form.id === 'journalForm') {
                e.preventDefault();
                const formData = new FormData(form);

                // Валідація: підстановка [MEDIA_ONLY], якщо текст порожній, але є файл
                const textContent = formData.get('content')?.toString().trim();
                const fileContent = formData.get('media');

                if (!textContent) {
                    if (fileContent && fileContent.size > 0) {
                        formData.set('content', '[MEDIA_ONLY]');
                    } else {
                        alert('Запис не може бути порожнім.');
                        return;
                    }
                }

                try {
                    const response = await fetch('/api/journal/create', {
                        method: 'POST',
                        headers: { 'X-CSRF-TOKEN': csrfToken },
                        body: formData
                    });
                    if (response.ok) {
                                        form.reset();
                                        document.getElementById('fileNameDisplay').textContent = '';
                                        document.getElementById('btnResetMainForm')?.classList.add('hidden');
                                        // Додано примусове приховування кнопки Опублікувати:
                                        document.getElementById('btnSubmitMainForm')?.classList.add('hidden');

                                        const preview = form.querySelector('.edit-media-preview');
                                        if (preview) preview.innerHTML = '';
                                        await window.loadJournalFeed();
                                    } else {
                        throw new Error('Помилка сервера');
                    }
                } catch (err) { alert('Помилка збереження запису.'); }
            }

            // --- Обробка інжектованих форм редагування ---
            if (form.matches('form[id^="journalEditForm-"]')) {
                e.preventDefault();
                const postId = form.getAttribute('data-id');
                const formData = new FormData(form);

                // Валідація для форми редагування
                const textContent = formData.get('content')?.toString().trim();
                const fileContent = formData.get('media');
                // Перевіряємо, чи є вже існуюче медіа, яке користувач не видалив
                const hasExistingMedia = form.querySelector('.current-media-display:not(.hidden)') !== null;

                if (!textContent) {
                    if ((fileContent && fileContent.size > 0) || hasExistingMedia) {
                        formData.set('content', '[MEDIA_ONLY]');
                    } else {
                        alert('Запис не може бути порожнім.');
                        return;
                    }
                }

                try {
                    const response = await fetch(`/api/journal/${postId}/update`, {
                        method: 'POST',
                        headers: { 'X-CSRF-TOKEN': csrfToken },
                        body: formData
                    });

                    if (response.status === 400) throw new Error('Некоректні дані (Bad Request)');
                    if (!response.ok) throw new Error('Помилка збереження змін');

                    await window.loadJournalFeed();
                } catch (err) {
                    alert('Не вдалося оновити пост: ' + err.message);
                }
            }
        });

    // Б) Перехоплення подій CHANGE для медіа-прев'ю (через FileReader)
    document.addEventListener('change', (e) => {
        if (e.target.matches('.journal-media-input')) {
            const file = e.target.files[0];
            const container = e.target.closest('form') || e.target.closest('.button-row');
            const previewContainer = container.querySelector('.edit-media-preview');
            const fileNameDisplay = container.querySelector('.file-name-display') || document.getElementById('fileNameDisplay');
            const currentMediaDisplay = container.querySelector('.current-media-display');

            if (fileNameDisplay) {
                fileNameDisplay.textContent = file ? file.name : '';
            }

            if (currentMediaDisplay) {
                currentMediaDisplay.classList.toggle('hidden', !!file);
            }

            if (!previewContainer) return;
            previewContainer.innerHTML = '';

            if (file) {
                const reader = new FileReader();
                reader.onload = function(event) {
                    if (file.type.startsWith('video/')) {
                        const video = document.createElement('video');
                        video.src = event.target.result;
                        video.controls = true;
                        video.classList.add('preview-video');
                        previewContainer.appendChild(video);
                    } else if (file.type.startsWith('image/')) {
                        const img = document.createElement('img');
                        img.src = event.target.result;
                        img.classList.add('preview-img');
                        previewContainer.appendChild(img);
                    }
                };
                reader.readAsDataURL(file);
            }
        }
    });

    // В) Керування видимістю кнопок основної форми (Скасувати / Опублікувати)
        const mainTextarea = document.getElementById('journalContent');
        const btnResetMain = document.getElementById('btnResetMainForm');
        const btnSubmitMain = document.getElementById('btnSubmitMainForm'); // Додали Опублікувати
        const journalMedia = document.getElementById('journalMedia');

        if (mainTextarea && btnResetMain && btnSubmitMain) {
            const toggleActionsVisibility = () => {
                const hasText = mainTextarea.value.trim().length > 0;
                const hasFile = journalMedia && journalMedia.files.length > 0;
                const shouldShow = hasText || hasFile;

                // Тоглимо обидві кнопки одночасно
                btnResetMain.classList.toggle('hidden', !shouldShow);
                btnSubmitMain.classList.toggle('hidden', !shouldShow);
            };

            mainTextarea.addEventListener('input', toggleActionsVisibility);
            if (journalMedia) journalMedia.addEventListener('change', toggleActionsVisibility);

            btnResetMain.addEventListener('click', () => {
                document.getElementById('journalForm')?.reset();
                document.getElementById('fileNameDisplay').textContent = '';
                document.querySelector('#journalForm .edit-media-preview').innerHTML = '';

                // Ховаємо обидві кнопки при скасуванні
                btnResetMain.classList.add('hidden');
                btnSubmitMain.classList.add('hidden');
            });
        }

});

/* =========================================
   ГЛОБАЛЬНІ ФУНКЦІЇ ДЛЯ ВИКЛИКУ З HTML
   ========================================= */

window.loadJournalFeed = async function() {
    const feedContainer = document.getElementById('journalFeed');

    if (!feedContainer) return;

    try {
        const response = await fetch('/api/journal/feed');
        if (!response.ok) throw new Error('Network response was not ok');

        feedContainer.innerHTML = await response.text();
        window.initJournalBehavior();
    } catch (err) {
        feedContainer.innerHTML = '<p class="text-center text-muted">Помилка завантаження стрічки.</p>';
    }
};

window.initJournalBehavior = function() {
    const allPosts = document.querySelectorAll('#journalFeed .journal-details');
    allPosts.forEach((post, index) => {
        if (index < 5) {
            post.setAttribute('open', 'true');
        }
    });
};

window.prepareEditPost = async function(postId, button) {
    // Шукаємо правильний контейнер
    const card = button.closest('.journal-post-card');
    // Якщо в HTML клас інакший, перевірте, чи це точно .journal-post-card
    // Використовуємо спеціальний контейнер замість заміни всієї картки
    const container = card.querySelector('.edit-form-container');

    if (!container) return;

    if (!container.classList.contains('hidden')) {
        container.classList.add('hidden');
        container.innerHTML = '';
        return;
    }

    try {
        const response = await fetch(`/api/journal/fragment/edit-form/${postId}`);
        if (!response.ok) throw new Error('Не вдалося завантажити форму');

        container.innerHTML = await response.text();
        container.classList.remove('hidden');
    } catch (err) {
        alert('Помилка: ' + err.message);
    }
};

window.cancelEditPost = function(button) {
    const container = button.closest('.edit-form-container');
    if (container) {
        container.classList.add('hidden');
        container.innerHTML = '';
    }
};

window.deletePost = async function(postId) {
    if (!confirm('Ви впевнені, що хочете видалити цей запис?')) return;

    try {
        const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
        const response = await fetch(`/api/journal/${postId}`, {
            method: 'DELETE',
            headers: { 'X-CSRF-TOKEN': csrfToken }
        });

        if (response.ok) {
            await window.loadJournalFeed();
        } else {
            alert('Не вдалося видалити пост.');
        }
    } catch (err) {
        alert('Помилка мережі: ' + err.message);
    }
};