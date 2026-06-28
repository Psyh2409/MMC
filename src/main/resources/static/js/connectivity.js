(function() {
    const banner = document.getElementById('offline-banner');

    async function checkConnection() {
        try {
            // Робимо запит до твого ж сайту, але без кешу
            const response = await fetch('/', { method: 'HEAD', cache: 'no-cache' });

            if (response.ok) {
                banner.classList.remove('is-active');
            }
        } catch (error) {
            // Сервер не відповідає — активуємо клас
            banner.classList.add('is-active');
        }
    }

    // Перевіряємо кожні 15 секунд
    setInterval(checkConnection, 15000);
})();