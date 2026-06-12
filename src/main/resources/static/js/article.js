document.addEventListener('DOMContentLoaded', () => {
    // Слухаємо всі кліки на сторінці
    document.body.addEventListener('click', (e) => {
        // Якщо клікнули на кнопку "Відповісти"
        if (e.target.classList.contains('reply-action-btn')) {
            const commentId = e.target.getAttribute('data-comment-id');
            window.moveReplyForm(commentId);
        }
    });
});
window.moveReplyForm = function(commentId) {
                    // 1. Шукаємо форму
                    const formBox = document.getElementById('main-comment-form-box');
                    if (!formBox) {
                        console.log("Форма не знайдена: користувач не авторизований");
                        return;
                    }

                    // 2. Шукаємо контейнер під коментарем
                    const targetContainer = document.getElementById('reply-form-container-' + commentId);

                    if (targetContainer) {
                        // 3. Переносимо форму в контейнер
                        targetContainer.appendChild(formBox);

                        // 4. Записуємо ID коментаря в приховане поле parentId
                        document.getElementById('parentIdInput').value = commentId;

                        // 5. Показуємо кнопку скасування і фокусуємось на тексті
                        document.getElementById('cancel-reply-btn').style.display = 'inline-block';
                        formBox.querySelector('textarea').focus();
                    } else {
                        console.error("Контейнер не знайдено для ID: " + commentId);
                    }
};

window.resetReplyForm = function() {
                // Повертаємо форму на початкове місце (перед списком коментарів)
                const formBox = document.getElementById('main-comment-form-box');
                const commentsList = document.getElementById('comments-container');

                if (formBox && commentsList) {
                    // Вставляємо форму назад перед контейнером усіх коментарів
                    commentsList.parentNode.insertBefore(formBox, commentsList);

                    // Очищаємо ID (тепер це знову новий незалежний коментар)
                    document.getElementById('parentIdInput').value = '';
                    document.getElementById('cancel-reply-btn').style.display = 'none';
                }
};


