document.addEventListener('DOMContentLoaded', () => {
    // Слухаємо всі кліки на сторінці (делегування подій)
    document.body.addEventListener('click', (e) => {
        // Якщо клікнули на кнопку "Відповісти"
        if (e.target.classList.contains('reply-action-btn')) {
            const commentId = e.target.getAttribute('data-comment-id');
            window.moveReplyForm(commentId);
        }
        // Якщо клікнули на кнопку "Редагувати"
        if (e.target.classList.contains('edit-comment-btn')) {
            const commentId = e.target.getAttribute('data-comment-id');
            window.moveEditForm(commentId);
        }
    });
});

window.moveReplyForm = function(commentId) {
    // Ховаємо форму редагування, якщо вона була відкрита
    window.cancelEditForm();

    const formBox = document.getElementById('main-comment-form-box');
    if (!formBox) {
        console.log("Форма не знайдена: користувач не авторизований");
        return;
    }

    const targetContainer = document.getElementById('reply-form-container-' + commentId);

    if (targetContainer) {
        targetContainer.appendChild(formBox);
        document.getElementById('parentIdInput').value = commentId;
        document.getElementById('cancel-reply-btn').style.display = 'inline-block';
        formBox.querySelector('textarea').focus();
    } else {
        console.error("Контейнер не знайдено для ID: " + commentId);
    }
};

window.resetReplyForm = function() {
    const formBox = document.getElementById('main-comment-form-box');
    const commentsList = document.getElementById('comments-container');

    if (formBox && commentsList) {
        commentsList.parentNode.insertBefore(formBox, commentsList);
        document.getElementById('parentIdInput').value = '';
        document.getElementById('cancel-reply-btn').style.display = 'none';
    }
};

window.moveEditForm = function(commentId) {
    // Скидаємо форму відповіді
    window.resetReplyForm();

    const existingEditForm = document.getElementById('active-edit-form');
    if (existingEditForm) existingEditForm.remove();

    const targetContainer = document.getElementById('edit-form-container-' + commentId);
    const currentTextEl = document.getElementById('comment-text-' + commentId);

    if (targetContainer && currentTextEl) {
        const currentText = currentTextEl.innerText.trim();

        const csrfInput = document.querySelector('input[name="_csrf"]');
        const csrfToken = csrfInput ? csrfInput.value : '';
        const csrfParam = csrfInput ? csrfInput.name : '_csrf';

        const editFormBox = document.createElement('div');
        editFormBox.id = 'active-edit-form';
        editFormBox.className = 'card-primary mt-sm mb-sm';

        editFormBox.innerHTML = `
            <form action="/articles/comments/${commentId}/edit" method="post" class="stack-form">
                <input type="hidden" name="${csrfParam}" value="${csrfToken}">
                <textarea name="content" class="comment-textarea" rows="3" required>${currentText}</textarea>
                <div class="button-row mt-xs">
                    <button type="submit" class="btn-primary btn-sm">Зберегти зміни</button>
                    <button type="button" onclick="window.cancelEditForm()" class="btn-outline btn-sm">Скасувати</button>
                </div>
            </form>
        `;

        targetContainer.appendChild(editFormBox);
        editFormBox.querySelector('textarea').focus();
    }
};

window.cancelEditForm = function() {
    const existingEditForm = document.getElementById('active-edit-form');
    if (existingEditForm) {
        existingEditForm.remove();
    }
};