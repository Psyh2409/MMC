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
});