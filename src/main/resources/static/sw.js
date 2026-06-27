const CACHE_NAME = 'mmc-v4'; // Оновив, щоб точно стерти всі старі помилки
const OFFLINE_URL = '/offline.html';

// 1. Кешуємо ТІЛЬКИ наш незалежний offline.html
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.add(OFFLINE_URL))
    );
    self.skipWaiting();
});

// 2. Видаляємо старе сміття
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => Promise.all(
            keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
        ))
    );
    self.clients.claim();
});

// 3. Перехоплюємо запити ТІЛЬКИ на сторінки (HTML)
self.addEventListener('fetch', (event) => {
    if (event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request).catch(() => caches.match(OFFLINE_URL))
        );
    }
    // Всі інші запити (CSS, JS) просто йдуть в мережу, ми їх не чіпаємо!
});