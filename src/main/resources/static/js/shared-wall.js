document.addEventListener('DOMContentLoaded', () => {
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

    // 1. Обробка форми додавання запису
    document.addEventListener('submit', async (e) => {
        const form = e.target;
        if (form.id === 'wallForm') {
            e.preventDefault();
            const formData = new FormData(form);
            const submitUrl = form.getAttribute('action');

            const textContent = formData.get('content')?.toString().trim();
            const fileContent = formData.get('media');

            if (!textContent && (!fileContent || fileContent.size === 0)) {
                alert('Повідомлення не може бути порожнім.');
                return;
            }

            try {
                const response = await fetch(submitUrl, {
                    method: 'POST',
                    headers: { 'X-CSRF-TOKEN': csrfToken },
                    body: formData
                });

                if (response.ok) {
                    // Отримуємо roomId з поточного URL браузера (наприклад: /therapy/room/123-456/...)
                    const roomId = window.location.pathname.split('/')[3];
                    // Перезавантажуємо першу сторінку стіни
                    await reloadWallFeed(roomId, 0, 5);
                } else {
                    throw new Error('Помилка сервера');
                }
            } catch (err) {
                alert('Помилка відправки: ' + err.message);
            }
        }
    });

    // 2. Керування кнопками форми (Глобальне делегування)
        function toggleWallActionsVisibility() {
            const mainTextarea = document.getElementById('wallContent');
            const wallMedia = document.getElementById('wallMedia');
            const btnResetMain = document.getElementById('btnResetWallForm');
            const btnSubmitMain = document.getElementById('btnSubmitWallForm');

            if (!mainTextarea || !btnResetMain || !btnSubmitMain) return;

            const hasText = mainTextarea.value.trim().length > 0;
            const hasFile = wallMedia && wallMedia.files && wallMedia.files.length > 0;
            const shouldShow = hasText || hasFile;

            if (shouldShow) {
                btnResetMain.classList.remove('hidden');
                btnSubmitMain.classList.remove('hidden');
            } else {
                btnResetMain.classList.add('hidden');
                btnSubmitMain.classList.add('hidden');
            }
        }

        document.addEventListener('input', (e) => {
            if (e.target && e.target.id === 'wallContent') {
                toggleWallActionsVisibility();
            }
        });

        document.addEventListener('change', (e) => {
            if (e.target && e.target.id === 'wallMedia') {
                const file = e.target.files[0];
                const fileNameDisplay = document.getElementById('wallFileNameDisplay');
                const previewContainer = document.querySelector('#wallForm .edit-media-preview');

                if (fileNameDisplay) {
                    fileNameDisplay.textContent = file ? file.name : '';
                }

                if (previewContainer) {
                    previewContainer.innerHTML = '';
                    if (file) {
                        const reader = new FileReader();
                        reader.onload = function(event) {
                            if (file.type.startsWith('video/')) {
                                const video = document.createElement('video');
                                video.src = event.target.result;
                                video.controls = true;
                                video.classList.add('media-preview');
                                previewContainer.appendChild(video);
                            } else if (file.type.startsWith('image/')) {
                                const img = document.createElement('img');
                                img.src = event.target.result;
                                img.classList.add('media-preview');
                                previewContainer.appendChild(img);
                            }
                        };
                        reader.readAsDataURL(file);
                    }
                }
                toggleWallActionsVisibility();
            }
        });

        document.addEventListener('click', (e) => {
            if (e.target && e.target.id === 'btnResetWallForm') {
                const form = document.getElementById('wallForm');
                if (form) form.reset();

                const fileNameDisplay = document.getElementById('wallFileNameDisplay');
                if (fileNameDisplay) fileNameDisplay.textContent = '';

                const previewContainer = document.querySelector('#wallForm .edit-media-preview');
                if (previewContainer) previewContainer.innerHTML = '';

                toggleWallActionsVisibility();
            }
        });
});

/* ГЛОБАЛЬНІ ФУНКЦІЇ ДЛЯ СТІНИ (Викликаються з кнопок) */

// Внутрішня функція перезавантаження
async function reloadWallFeed(roomId, page, size) {
    try {
        const feedUrl = `/api/room/${roomId}/wall/fragment?page=${page}&size=${size}`;
        const response = await fetch(feedUrl);
        if (!response.ok) throw new Error('Network response was not ok');

        const feedContainer = document.getElementById('wallFeed');
        if (feedContainer) {
            feedContainer.innerHTML = await response.text();
            if (typeof window.applyMediaFacades === 'function') {
                window.applyMediaFacades();
            }
        }
    } catch (err) {
        console.error('Помилка завантаження стіни:', err);
    }
}

// Функція для кнопок пагінації
window.loadWallPage = async function(btn) {
    const roomId = btn.getAttribute('data-room');
    const page = btn.getAttribute('data-page') || 0;
    const size = btn.getAttribute('data-size') || 5;
    await reloadWallFeed(roomId, page, size);
};

// Функція для видалення
window.deleteWallPost = async function(btn) {
    if (!confirm('Видалити це повідомлення зі спільної стіни?')) return;

    const postId = btn.getAttribute('data-id');
    const roomId = btn.getAttribute('data-room');
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

    try {
        const response = await fetch(`/api/room/${roomId}/wall/${postId}`, {
            method: 'DELETE',
            headers: { 'X-CSRF-TOKEN': csrfToken }
        });

        if (response.ok) {
            // Перезавантажуємо поточну стіну (першу сторінку)
            await reloadWallFeed(roomId, 0, 5);
        } else {
            alert('Не вдалося видалити повідомлення.');
        }
    } catch (err) {
        alert('Помилка мережі: ' + err.message);
    }
};

// Підготовка форми редагування поста
window.prepareEditWallPost = function(postId, roomId, buttonElement) {
    console.log('[SharedWall] prepareEditWallPost викликано:', { postId, roomId });

    const postCard = buttonElement.closest('.journal-post-card');
    const editContainer = postCard.querySelector('.edit-form-container');

    if (!editContainer) {
        console.error('[SharedWall] edit-form-container не знайдено');
        return;
    }

    console.log('[SharedWall] Завантаження форми редагування...');

    // Завантаження форми редагування з сервера
    fetch(`/api/room/${roomId}/wall/fragment/edit-form/${postId}`)
        .then(response => {
            console.log('[SharedWall] Response status:', response.status);
            if (!response.ok) {
                throw new Error('Помилка завантаження форми редагування');
            }
            return response.text();
        })
        .then(html => {
            console.log('[SharedWall] HTML отримано, довжина:', html.length);
            editContainer.innerHTML = html;
            editContainer.classList.remove('hidden');

            // Приховати оригінальний контент поста (якщо існує)
            const postContent = postCard.querySelector('.journal-post-content');
            if (postContent) postContent.classList.add('hidden');

            const postMedia = postCard.querySelector('.journal-post-media-box');
            if (postMedia) postMedia.classList.add('hidden');

            // Приховати кнопки дій (якщо існують)
            const postActions = postCard.querySelector('.post-actions');
            if (postActions) postActions.classList.add('hidden');

            // Налаштувати обробку форми редагування
            const editForm = editContainer.querySelector('form');
            if (editForm) {
                console.log('[SharedWall] Форма знайдена, встановлення action:', `/api/room/${roomId}/wall/${postId}/update`);
                editForm.action = `/api/room/${roomId}/wall/${postId}/update`;
                editForm.addEventListener('submit', function(e) {
                    e.preventDefault();
                    handleEditFormSubmit(editForm, postId, roomId, postCard);
                });
            } else {
                console.error('[SharedWall] Форма не знайдена в завантаженому HTML');
            }
        })
        .catch(error => {
            console.error('[SharedWall] Помилка:', error);
            alert('Не вдалося завантажити форму редагування');
        });
};

// Скасування редагування поста
window.cancelEditWallPost = function(buttonElement) {
    const postCard = buttonElement.closest('.journal-post-card');
    const editContainer = postCard.querySelector('.edit-form-container');

    // Очистити контейнер
    editContainer.innerHTML = '';
    editContainer.classList.add('hidden');

    // Показати оригінальний контент поста
    const postContent = postCard.querySelector('.journal-post-content');
    const postMedia = postCard.querySelector('.journal-post-media-box');
    if (postContent) postContent.classList.remove('hidden');
    if (postMedia) postMedia.classList.remove('hidden');

    // Показати кнопки дій
    const postActions = postCard.querySelector('.post-actions');
    if (postActions) postActions.classList.remove('hidden');
};

// Обробка відправки форми редагування
function handleEditFormSubmit(form, postId, roomId, postCard) {
    console.log('[SharedWall] handleEditFormSubmit викликано');

    const formData = new FormData(form);

    // Додати CSRF токен
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

    fetch(form.action, {
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': csrfToken },
        body: formData
    })
    .then(response => {
        console.log('[SharedWall] Response status:', response.status);
        if (!response.ok) {
            throw new Error('Помилка оновлення поста');
        }
        return response;
    })
    .then(() => {
        console.log('[SharedWall] Пост успішно оновлено');
        // Закрити форму редагування і показати оновлений контент
        const editContainer = postCard.querySelector('.edit-form-container');
        editContainer.innerHTML = '';
        editContainer.classList.add('hidden');

        // Показати оновлений контент
        const postContent = postCard.querySelector('.journal-post-content');
        const postMedia = postCard.querySelector('.journal-post-media-box');
        if (postContent) postContent.classList.remove('hidden');
        if (postMedia) postMedia.classList.remove('hidden');

        // Показати кнопки дій
        const postActions = postCard.querySelector('.post-actions');
        if (postActions) postActions.classList.remove('hidden');

        // Перезавантажити стіну для відображення оновлень
        reloadWallFeed(roomId, 0, 5);
    })
    .catch(error => {
        console.error('[SharedWall] Помилка:', error);
        alert('Не вдалося оновити пост');
    });
}
