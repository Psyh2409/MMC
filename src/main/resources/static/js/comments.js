document.addEventListener('click', function(event) {

    // 1. Логіка для кнопки "Відповісти"
    const replyBtn = event.target.closest('.btn-reply-action');
    if (replyBtn) {
        const commentId = replyBtn.getAttribute('data-id');
        const actionUrl = replyBtn.getAttribute('data-url'); // Беремо точний URL з кнопки
        const commentActions = replyBtn.closest('.comment-actions');

        // Якщо форма вже відкрита - скасовуємо (ховаємо)
        let existingForm = commentActions.nextElementSibling;
        if (existingForm && existingForm.classList.contains('reply-form-wrapper')) {
            existingForm.remove();
            return;
        }

        // Беремо CSRF-токен глобально зі сторінки
        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
        const csrfInput = `<input type="hidden" name="_csrf" value="${csrfToken}">`;

        // Генеруємо HTML форми відповіді
        const formHtml = `
            <div class="reply-form-wrapper mt-sm pl-md border-left">
                <form action="${actionUrl}" method="post" class="stack-form">
                    ${csrfInput}
                    <input type="hidden" name="parentId" value="${commentId}">
                    <textarea name="content" class="form-textarea" rows="2" placeholder="Ваша відповідь..." required></textarea>
                    <div class="text-right mt-xs">
                        <button type="submit" class="btn-primary btn-sm">Відповісти</button>
                    </div>
                </form>
            </div>
        `;

        commentActions.insertAdjacentHTML('afterend', formHtml);
        return;
    }

    // 2. Логіка для кнопки "Редагувати"
    const editBtn = event.target.closest('.btn-edit-action');
    if (editBtn) {
        const commentId = editBtn.getAttribute('data-id');
        const commentNode = editBtn.closest('.comment-card-nested');
        const textContainer = commentNode.querySelector('.comment-text');
        const commentActions = editBtn.closest('.comment-actions');

        // Якщо вже редагуємо - скасовуємо
        if (commentNode.classList.contains('is-editing')) {
            commentNode.classList.remove('is-editing');
            textContainer.classList.remove('is-hidden');
            const editForm = commentNode.querySelector('.edit-form-wrapper');
            if (editForm) editForm.remove();
            return;
        }

        const currentText = textContainer.querySelector('p').innerText.trim();

        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
        const csrfInput = `<input type="hidden" name="_csrf" value="${csrfToken}">`;

        // URL редагування універсальний і працює через існуючий контролер
        const formHtml = `
            <div class="edit-form-wrapper mt-sm">
                <form action="/articles/comments/${commentId}/edit" method="post" class="stack-form">
                    ${csrfInput}
                    <textarea name="content" class="form-textarea" rows="2" required>${currentText}</textarea>
                    <div class="text-right mt-xs">
                        <button type="submit" class="btn-primary btn-sm">Зберегти</button>
                    </div>
                </form>
            </div>
        `;

        commentNode.classList.add('is-editing');
        textContainer.classList.add('is-hidden');
        commentActions.insertAdjacentHTML('beforebegin', formHtml);
    }
});