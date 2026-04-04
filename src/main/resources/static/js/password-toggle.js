document.addEventListener('click', function (e) {
    const toggleBtn = e.target.closest('#togglePassword');
    if (!toggleBtn) {
        return;
    }

    const passwordInput = document.getElementById('password');
    if (!passwordInput) {
        console.error("Поле з id='password' не знайдено!");
        return;
    }

    const isPassword = passwordInput.getAttribute('type') === 'password';
    passwordInput.setAttribute('type', isPassword ? 'text' : 'password');
    toggleBtn.textContent = isPassword ? '🕶️' : '👁️';
});
