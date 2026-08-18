document.addEventListener('DOMContentLoaded', () => {

    console.log("=== DEBUG CONFIG ===", window.TherapyConfig);
    const config = window.TherapyConfig;
    if (!config) {
        console.error("Критична помилка: Конфігурація кімнати не завантажена.");
        return;
    }

    const appId = 'vpaas-magic-cookie-a6c49e33cd42404bb9c7e3d27f7825c6';

    // Змінна для зберігання об'єкта Jitsi
    let jitsiApi = null;

    // Функція ТІЛЬКИ створює Jitsi, але НЕ викликається сама по собі
    const initJitsi = () => {
        const container = document.querySelector('#jitsi-container');
        if (!container) return;

        // Важливо: очищаємо контейнер перед новим створенням, щоб не було дублів
        container.innerHTML = '';

        const options = {
            roomName: `${appId}/${config.roomName}`,
            jwt: config.jitsiJwt,
            width: '100%',
            height: '100%',
            parentNode: container,
            userInfo: { displayName: config.userName },
            configOverwrite: { prejoinPageEnabled: false },
            interfaceConfigOverwrite: { SHOW_JITSI_WATERMARK: false, TILE_VIEW_MAX_COLUMNS: 2 }
        };

        jitsiApi = new JitsiMeetExternalAPI("8x8.vc", options);
    };

    // =========================================================
        // БЛОК УПРАВЛІННЯ КНОПКОЮ (РОЗГОРНУТИ / ЗГОРНУТИ)
        // =========================================================
        const toggleBtn = document.getElementById('toggle-session-btn');
        const sessionArea = document.getElementById('active-session-area'); // Це блок, де лежить відео і нотатки

        if (toggleBtn && sessionArea) {
            toggleBtn.addEventListener('click', () => {
                if (sessionArea.classList.contains('hidden')) {
                    // 1. РОЗГОРТАЄМО
                    sessionArea.classList.remove('hidden');
                    toggleBtn.innerHTML = '<span>❌</span> Згорнути відеосесію';

                    // ЗАПУСКАЄМО JITSI
                    initJitsi();
                } else {
                    // 2. ЗГОРТАЄМО
                    sessionArea.classList.add('hidden');
                    toggleBtn.innerHTML = '<span>📹</span> Розгорнути відеосесію';

                    // ЖОРСТКО ВБИВАЄМО З'ЄДНАННЯ
                    if (jitsiApi !== null) {
                        jitsiApi.dispose();
                        jitsiApi = null;
                    }

                    // --- ОЧИЩЕННЯ НОТАТОК ---
                    const notesArea = document.getElementById('session-notes');
                    if (notesArea) {
                        notesArea.value = ''; // Очищаємо екран
                    }

                    if (typeof currentNoteId !== 'undefined') {
                        currentNoteId = null; // Обнуляємо ID для створення нового запису в БД
                    }

                    const syncStatus = document.getElementById('sync-status');
                    if (syncStatus) {
                        syncStatus.innerText = 'Очікування...';
                    }
                }
            });
        }

    // --- БЛОК 2: АВТОЗБЕРЕЖЕННЯ ---
    const notesArea = document.getElementById('session-notes');
    const statusLabel = document.getElementById('sync-status');

    let saveTimeout = null;
    let currentNoteId = null;

    if (notesArea && statusLabel) {
        notesArea.addEventListener('input', () => {
            statusLabel.innerText = "Збереження...";
            statusLabel.style.color = "var(--text-secondary)";

            clearTimeout(saveTimeout);
            saveTimeout = setTimeout(async () => {
                try {
                    let url = `/therapy/notes/save/${config.roomId}`;
                    if (currentNoteId) {
                        url += `?noteId=${currentNoteId}`;
                    }

                    const response = await fetch(url, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'text/plain;charset=UTF-8',
                            [config.csrfHeader]: config.csrfToken
                        },
                        body: notesArea.value
                    });

                    if (response.ok) {
                        const textRes = await response.text();
                        if (textRes) {
                            try {
                                const data = JSON.parse(textRes);
                                if (data && data.noteId) currentNoteId = data.noteId;
                            } catch(e) {}
                        }

                        statusLabel.innerText = "Дані збережено";
                        statusLabel.style.color = "var(--status-success)";

                        setTimeout(() => {
                            if(statusLabel.innerText === "Дані збережено") statusLabel.innerText = "";
                        }, 3000);
                    } else {
                        throw new Error('Помилка сервера');
                    }
                } catch (e) {
                    console.error("Помилка автозбереження:", e);
                    statusLabel.innerText = "Помилка зв'язку";
                    statusLabel.style.color = "var(--status-error)";
                }
            }, 2000);
        });
    }

    // --- БЛОК 3: ЛОГІКА МОДАЛЬНОГО ВІКНА ---
    const modal = document.getElementById('history-modal');
    const historyList = document.getElementById('history-list');
    const btnShowHistory = document.getElementById('btn-show-history');
    const btnCloseModal = document.getElementById('close-modal');

    if (btnShowHistory && modal) {
        const toggleHistory = async () => {
            const isVisible = modal.classList.contains('is-visible');

            if (isVisible) {
                modal.classList.remove('is-visible');
            } else {
                modal.classList.add('is-visible');
                historyList.innerHTML = '<span class="status-loading">Синхронізація з архівом...</span>';

                try {
                    const response = await fetch(`/therapy/notes/history/${config.roomId}`);
                    if (response.ok) {
                        const notes = await response.json();
                        if (notes.length === 0) {
                            historyList.innerHTML = '<p class="status-loading">Записів за історію не знайдено.</p>';
                        } else {
                            historyList.innerHTML = notes.map(n => `
                                <div class="history-item">
                                    <span class="history-date">${new Date(n.createdAt).toLocaleString('uk-UA')}</span>
                                    <div class="history-text">${n.content}</div>
                                </div>
                            `).join('');
                        }
                    }
                } catch (e) {
                    historyList.innerHTML = '<p class="status-error">Помилка завантаження історії.</p>';
                }
            }
        };

        btnShowHistory.addEventListener('click', toggleHistory);
        btnCloseModal.addEventListener('click', () => modal.classList.remove('is-visible'));
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.classList.remove('is-visible');
        });
    }

    // --- БЛОК 4: КЕРУВАННЯ СТАНОМ КІМНАТИ (Для фахівця) ---
        if (config.isTherapistInThisRoom) {
            // 1. Старий запобіжник: якщо фахівець таки закрив вкладку - гасимо кімнату
            window.addEventListener('beforeunload', () => {
                fetch('/therapy/room/' + config.roomId + '/leave', {
                    method: 'POST',
                    headers: { [config.csrfHeader]: config.csrfToken, 'Content-Type': 'application/json' },
                    keepalive: true
                });
            });

            // 2. НАШ НОВИЙ ПУЛЬТ: слухаємо клік по кнопці "Розгорнути / Згорнути"
            const toggleBtn = document.querySelector('#toggle-session-btn');
            const sessionArea = document.querySelector('#active-session-area');

            if (toggleBtn && sessionArea) {
                toggleBtn.addEventListener('click', () => {
                    // Даємо браузеру 50 мілісекунд, щоб він встиг локально перемкнути CSS-клас 'hidden'
                    setTimeout(() => {
                        const isCollapsed = sessionArea.classList.contains('hidden');
                        const endpoint = isCollapsed ? '/leave' : '/activate';

                        fetch('/therapy/room/' + config.roomId + endpoint, {
                            method: 'POST',
                            headers: {
                                [config.csrfHeader]: config.csrfToken,
                                'Content-Type': 'application/json'
                            }
                        });
                    }, 50);
                });
            }
        }
        // --- БЛОК 5: ОЧІ КЛІЄНТА ТА ПРИМУСОВА КАТАПУЛЬТА ---
            if (!config.isTherapistInThisRoom) {
                const toggleBtnWrapper = document.getElementById('session-toggle-wrapper');
                const sessionArea = document.getElementById('active-session-area');
                const toggleBtn = document.getElementById('toggle-session-btn');

                if (toggleBtnWrapper && sessionArea) {
                    setInterval(() => {
                        fetch('/therapy/room/' + config.roomId + '/status')
                            .then(response => response.json())
                            .then(isActive => {
                                if (isActive) {
                                    if (toggleBtnWrapper.classList.contains('hidden')) {
                                        toggleBtnWrapper.classList.remove('hidden');
                                        window.scrollTo({ top: 0, behavior: 'smooth' });

                                        // ЯСКРАВЕ СПОВІЩЕННЯ ДЛЯ КЛІЄНТА
                                        triggerLoudNotification();
                                    }
                                } else {
                                    toggleBtnWrapper.classList.add('hidden');
                                    closeLoudNotification();

                                    if (!sessionArea.classList.contains('hidden')) {
                                        sessionArea.classList.add('hidden');
                                        if (toggleBtn) {
                                            toggleBtn.innerHTML = '<span>📹</span> Розгорнути відеосесію';
                                        }
                                        if (jitsiApi !== null) {
                                            jitsiApi.dispose();
                                            jitsiApi = null;
                                        }
                                    }
                                }
                            })
                            .catch(err => console.log("Спільний простір очікує..."));
                    }, 3000);
                }
            }

            // ===============================================
            // ФУНКЦІЇ ДИНАМІЧНОГО СПОВІЩЕННЯ КЛІЄНТА
            // ===============================================
            function triggerLoudNotification() {
                if (document.getElementById('call-alert-modal')) return;

                const modalHtml = `
                    <div id="call-alert-modal" class="modal-overlay is-visible">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h3 class="modal-title">📞 Фахівець очікує!</h3>
                            </div>
                            <div class="p-md text-center">
                                <p class="text-main mb-md">Терапевтичну сесію розпочато. Ваш фахівець уже в відеокабінеті.</p>
                                <button id="btn-join-now" class="btn-primary w-100">
                                    🎥 Приєднатися до відеодзвінка
                                </button>
                            </div>
                        </div>
                    </div>
                `;

                document.body.insertAdjacentHTML('beforeend', modalHtml);

                document.getElementById('btn-join-now').addEventListener('click', () => {
                    const mainToggleBtn = document.getElementById('toggle-session-btn');
                    if (mainToggleBtn) mainToggleBtn.click();
                    closeLoudNotification();
                });
            }

            function closeLoudNotification() {
                const modal = document.getElementById('call-alert-modal');
                if (modal) modal.remove();
            }

            // === ЛОГІКА ЗАПРОШЕННЯ АДМІНІСТРАТОРА (SOS) — ЗБЕРЕЖЕНО НА МІСЦІ ===
            const initSosBtn = document.getElementById('init-sos-btn');
            const sosForm = document.getElementById('sos-request-form');
            const cancelSosBtn = document.getElementById('cancel-sos-btn');

            if (initSosBtn && sosForm && cancelSosBtn) {
                initSosBtn.addEventListener('click', () => {
                    initSosBtn.classList.add('hidden');
                    sosForm.classList.remove('hidden');
                });

                cancelSosBtn.addEventListener('click', () => {
                    sosForm.classList.add('hidden');
                    initSosBtn.classList.remove('hidden');
                    sosForm.reset();
                });
            }
            // ===============================================
        });