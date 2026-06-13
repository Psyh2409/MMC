document.addEventListener('DOMContentLoaded', function() {
    // Функція-помічник, щоб не дублювати код
    function setupToggle(buttonId, inputId) {
        const btn = document.getElementById(buttonId);
        const input = document.getElementById(inputId);

        if (btn && input) {
            btn.addEventListener('click', function (e) {
                // Запобігаємо випадковій відправці форми
                e.preventDefault();

                // Перемикаємо тип
                const isPassword = input.type === 'password';
                input.type = isPassword ? 'text' : 'password';

                // Міняємо іконку
                this.textContent = isPassword ? '🔒' : '👁️';
            });
        }
    }

    // Запускаємо для обох полів
    setupToggle('togglePassword', 'password');
    setupToggle('toggleConfirmPassword', 'confirmPassword');

    // 2. ІНЖЕНЕРНИЙ ФІКС: Пряме перемикання кнопки хедера без дурних пошуків
        // Якщо у тебе в HTML-шаблоні хедера для цієї кнопки прописано ID або унікальний клас,
        // ми б'ємо точно в ціль. Наприклад, використовуємо ID 'navLoginBtn' або твій клас:
        const loginBtn = document.getElementById('navLoginBtn') || document.querySelector('.btn-login-header');

        if (loginBtn) {
            loginBtn.textContent = 'Створити акаунт';
            loginBtn.href = '/register'; // Твій URL сторінки реєстрації
        }

});