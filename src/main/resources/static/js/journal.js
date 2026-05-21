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
        slider.addEventListener('input', (e) => {
            updateTheme(e.target.value, '75%', 'custom');
        });
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

        avatarFileInput.addEventListener('change', function() {
            const file = avatarFileInput.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('avatar', file);

            avatarContainer.style.opacity = '0.5';

            fetch('/api/profile/avatar', {
                method: 'POST',
                headers: { 'X-CSRF-TOKEN': csrfToken },
                body: formData
            })
            .then(response => {
                if (!response.ok) throw new Error('Помилка сервера');
                return response.json();
            })
            .then(data => {
                avatarContainer.style.opacity = '1';
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
            })
            .catch(error => {
                avatarContainer.style.opacity = '1';
                alert('Не вдалося оновити фото: ' + error.message);
            });
        });
    }

    /* =========================================
       3. УПРАВЛІННЯ ВКЛАДКАМИ (TABS)
       ========================================= */
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabPanes = document.querySelectorAll('.tab-pane');

    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetId = btn.getAttribute('data-target');
            tabButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            tabPanes.forEach(pane => {
                if (pane.id === targetId) {
                    pane.classList.add('active');
                    if (targetId === 'journal-tab') loadJournalFeed();
                } else {
                    pane.classList.remove('active');
                }
            });
        });
    });

    /* =========================================
       4. ДИНАМІКА ЩОДЕННИКА
       ========================================= */
    const journalForm = document.getElementById('journalForm');
    const journalFeed = document.getElementById('journalFeed');
    const journalMedia = document.getElementById('journalMedia');
    const fileNameDisplay = document.getElementById('fileNameDisplay');
    const mainTextarea = document.getElementById('journalContent');
    const btnResetMain = document.getElementById('btnResetMainForm');

    if (mainTextarea && btnResetMain) {
        const toggleResetVisibility = () => {
            const hasText = mainTextarea.value.trim().length > 0;
            const hasFile = journalMedia && journalMedia.files.length > 0;
            btnResetMain.classList.toggle('hidden', !(hasText || hasFile));
        };
        mainTextarea.addEventListener('input', toggleResetVisibility);
        if (journalMedia) journalMedia.addEventListener('change', toggleResetVisibility);
        btnResetMain.addEventListener('click', () => {
            if (journalForm) journalForm.reset();
            if (fileNameDisplay) fileNameDisplay.textContent = '';
            btnResetMain.classList.add('hidden');
        });
    }

    if (journalMedia && fileNameDisplay) {
        journalMedia.addEventListener('change', (e) => {
            fileNameDisplay.textContent = e.target.files[0] ? e.target.files[0].name : '';
        });
    }

    if (journalForm) {
        journalForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const formData = new FormData(journalForm);
            try {
                const response = await fetch('/api/journal/create', {
                    method: 'POST',
                    headers: { 'X-CSRF-TOKEN': csrfToken },
                    body: formData
                });
                if (response.ok) {
                    journalForm.reset();
                    if (fileNameDisplay) fileNameDisplay.textContent = '';
                    if (btnResetMain) btnResetMain.classList.add('hidden');
                    await loadJournalFeed();
                }
            } catch (err) { alert('Помилка мережі.'); }
        });
    }

    /* =========================================
       5. БАР'ЄР ДЕАКТИВАЦІЇ
       ========================================= */
    const toggleBtn = document.getElementById('toggleDeactivationBtn');
    const vault = document.getElementById('deactivationVault');
    if (toggleBtn && vault) {
        toggleBtn.addEventListener('click', () => vault.classList.toggle('active'));
    }

    /* =========================================
       6. ПЕРЕМИКАЧ ПАРОЛЯ
       ========================================= */
    document.querySelectorAll('.btn-toggle-password').forEach(btn => {
        btn.addEventListener('click', function() {
            const input = this.previousElementSibling;
            input.type = input.type === 'password' ? 'text' : 'password';
            this.textContent = input.type === 'password' ? '👁️' : '🔒';
        });
    });
});

/* =========================================
   ГЛОБАЛЬНІ ФУНКЦІЇ (ВИХІД ЗА DOMContentLoaded)
   ========================================= */
async function loadJournalFeed() {
    const journalFeed = document.getElementById('journalFeed');
    if (!journalFeed) return;
    try {
        const response = await fetch('/api/journal/feed');
        journalFeed.innerHTML = await response.text();
    } catch (err) { journalFeed.innerHTML = '<p>Помилка завантаження.</p>'; }
};

async function prepareEditPost(postId, button) {
    const card = button.closest('.journal-post-card');
    const originalHtml = card.innerHTML;
    try {
        const response = await fetch(`/api/journal/fragment/edit-form/${postId}`);
        card.innerHTML = await response.text();
        card.querySelector('.btn-cancel-inline').addEventListener('click', () => card.innerHTML = originalHtml);
        card.querySelector('form').addEventListener('submit', async (e) => {
            e.preventDefault();
            await fetch(`/api/journal/${postId}/update`, { method: 'POST', headers: { 'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value }, body: new FormData(e.target) });
            await window.loadJournalFeed();
        });
    } catch (err) { alert('Помилка редагування.');
    }
};

window.deletePost = async function(postId) {
    if (!confirm('Ви впевнені, що хочете видалити цей запис?')) {
        return;
    }

    try {
        const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
        // Зазвичай у Spring Boot видалення робиться через DELETE-запит
        // або POST-запит на url з /delete. Підстав свій мапінг з контролера, якщо він інший.
        const response = await fetch(`/api/journal/${postId}`, {
            method: 'DELETE', // Зміни на 'DELETE', якщо в контролері @DeleteMapping
            headers: {
                'X-CSRF-TOKEN': csrfToken
            }
        });

        if (response.ok) {
            await window.loadJournalFeed(); // Оновлюємо стрічку після видалення
        } else {
            alert('Не вдалося видалити пост. Перевірте консоль сервера.');
        }
    } catch (err) {
        alert('Помилка мережі: ' + err.message);
    }
};