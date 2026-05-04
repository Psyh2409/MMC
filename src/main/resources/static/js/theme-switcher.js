document.addEventListener('DOMContentLoaded', () => {
    const toggleBtn = document.getElementById('theme-toggle');
    const htmlElement = document.documentElement;

    // 1. Беремо збережений режим освітлення (light або dark)
    const savedMode = localStorage.getItem('mmc-mode') || 'light';
    setMode(savedMode);

    // 2. Логіка кліку по кнопці
    if (toggleBtn) {
        toggleBtn.addEventListener('click', () => {
            const currentMode = htmlElement.getAttribute('data-mode') || 'light';
            const newMode = currentMode === 'light' ? 'dark' : 'light';
            setMode(newMode);
        });
    }

    // 3. Функція застосування режиму
    function setMode(modeName) {
        // Керуємо саме data-mode, не чіпаючи твої кольорові теми
        htmlElement.setAttribute('data-mode', modeName);
        localStorage.setItem('mmc-mode', modeName);

        // Змінюємо іконку: якщо світло - пропонуємо місяць, якщо темно - сонце
        if (toggleBtn) {
            toggleBtn.textContent = modeName === 'light' ? '🌙' : '☀️';
        }
    }
});