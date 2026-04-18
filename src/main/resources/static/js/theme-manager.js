/* theme-manager.js — Інтелектуальне керування атмосферою */

class ThemeEngine {
    constructor() {
        this.waveInterval = null;
        this.waveAngle = 0;

        // Читаємо пам'ять (дефолт: прибій увімкнено)
        this.isActive = localStorage.getItem('wave-active') !== 'false';

        // Швидкість (крок синусоїди).
        // 0.01 - дуже повільне дихання, 0.1 - швидке. Дефолт: 0.04
        this.waveSpeed = parseFloat(localStorage.getItem('wave-speed')) || 0.04;
    }

    // 1. ІНІЦІАЛІЗАЦІЯ (Запуск при старті)
    init() {
        const savedTheme = localStorage.getItem('user-theme') || 'blue';
        const savedMode = localStorage.getItem('user-mode') || 'day';

        document.documentElement.setAttribute('data-theme', savedTheme);
        document.documentElement.setAttribute('data-mode', savedMode);

        if (this.isActive) {
            this.startWave();
        }
    }

    // 2. ЗМІНА ТЕМИ
    setTheme(themeName) {
        document.documentElement.setAttribute('data-theme', themeName);
        localStorage.setItem('user-theme', themeName);
    }

    // 3. НІЧНИЙ РЕЖИМ (Якщо він є)
    toggleNightMode() {
        const currentMode = document.documentElement.getAttribute('data-mode') || 'day';
        const newMode = (currentMode === 'night') ? 'day' : 'night';
        document.documentElement.setAttribute('data-mode', newMode);
        localStorage.setItem('user-mode', newMode);
    }

    // 4. КЕРУВАННЯ ПРИБОЄМ (Увімк/Вимк)
    toggleWave(state) {
        this.isActive = state;
        localStorage.setItem('wave-active', state);
        if (state) {
            this.startWave();
        } else {
            this.stopWave();
        }
    }

    // 5. НАЛАШТУВАННЯ ТЕМПОРИТМУ
    setTempo(speedValue) {
        this.waveSpeed = parseFloat(speedValue);
        localStorage.setItem('wave-speed', this.waveSpeed);
    }

    // ВНУТРІШНІЙ ДВИГУН: Запуск синусоїди
    startWave() {
        if (this.waveInterval) clearInterval(this.waveInterval);

        this.waveInterval = setInterval(() => {
            // Коливання від 0.93 до 1.07 (як ви і прописали)
            const brightnessValue = 1 + Math.sin(this.waveAngle) * 0.15;
            document.documentElement.style.setProperty('--wave-brightness', brightnessValue);

            // Рух хвилі. Чим більший this.waveSpeed, тим швидше пульсує
            this.waveAngle += this.waveSpeed;
        }, 25);
    }

    // ВНУТРІШНІЙ ДВИГУН: Зупинка
    stopWave() {
        if (this.waveInterval) {
            clearInterval(this.waveInterval);
            this.waveInterval = null;
        }
        // Скидаємо яскравість до норми (1)
        document.documentElement.style.setProperty('--wave-brightness', 1);
    }
}

// Створюємо та запускаємо рушій
const engine = new ThemeEngine();
engine.init();

// --- ПРИВ'ЯЗКА ЕЛЕМЕНТІВ УПРАВЛІННЯ ---
document.addEventListener('DOMContentLoaded', () => {
    const surfToggle = document.getElementById('surf-toggle');
    const tempoSlider = document.getElementById('tempo-slider');

    // Синхронізуємо HTML з пам'яттю при завантаженні
    if (surfToggle) surfToggle.checked = engine.isActive;
    if (tempoSlider) tempoSlider.value = engine.waveSpeed;

    // Слухачі подій
    if (surfToggle) {
        surfToggle.addEventListener('change', (e) => engine.toggleWave(e.target.checked));
    }

    if (tempoSlider) {
        tempoSlider.addEventListener('input', (e) => engine.setTempo(e.target.value));
    }
});

// Глобальний перехоплювач кліків для кнопок вибору теми
document.addEventListener('click', (e) => {
    // Якщо клікнули на кружечок кольору
    if (e.target.classList.contains('theme-circle')) {
        e.preventDefault(); // Зупиняє перезавантаження сторінки

        // Знаходимо атрибут onclick і дістаємо з нього назву теми (наприклад, 'red')
        const onclickAttr = e.target.getAttribute('onclick');
        if (onclickAttr && onclickAttr.includes('setTheme')) {
            const themeName = onclickAttr.match(/'([^']+)'/)[1];
            setTheme(themeName); // Викликаємо зміну теми безпечно
        }
    }
});