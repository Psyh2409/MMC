document.addEventListener('DOMContentLoaded', () => {
    // Беремо змінні, які дбайливо передав Thymeleaf
    const config = window.ROOM_CONFIG;
//                // --- ГЛОБАЛЬНІ ЗМІННІ (Thymeleaf -> JS) ---
//                const roomName = /*[[${roomName}]]*/ 'default-room';
//                const userName = /*[[${currentUser.name}]]*/ 'Користувач';
//                const clientUuid = /*[[${client.id}]]*/ '';
//                const jitsiJwt = /*[[${jitsiJwt}]]*/ null;
//
                // 🟢 ДОДАНО: Перевіряємо, чи сторінку відкрив терапевт/адмін
//                const isProfessional = [[${isAdmin || isTherapist}]] false;

                const appId = 'vpaas-magic-cookie-a6c49e33cd42404bb9c7e3d27f7825c6';

                const csrfToken = document.querySelector('meta[name="_csrf"]').content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

                // --- БЛОК 1: ІНІЦІАЛІЗАЦІЯ ВІДЕО JITSI ---
                let jitsiApi = null;
                const initJitsi = () => {
                    const container = document.querySelector('#jitsi-container');
                    if (!container) return;

                    const options = {
                        roomName: `${appId}/${roomName}`,
                        jwt: jitsiJwt,
                        width: '100%',
                        height: '100%',
                        parentNode: container,
                        userInfo: { displayName: userName },
                        configOverwrite: { prejoinPageEnabled: false },
                        interfaceConfigOverwrite: { SHOW_JITSI_WATERMARK: false, TILE_VIEW_MAX_COLUMNS: 2 }
                    };

                    jitsiApi = new JitsiMeetExternalAPI("8x8.vc", options);
                    jitsiApi.addEventListener('videoConferenceJoined', () => {
                        jitsiApi.executeCommand('toggleTileView');
                    });
                };
                initJitsi();

                // --- БЛОК 2: АВТОЗБЕРЕЖЕННЯ (Stateless / Чистий аркуш) ---
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
                                let url = `/therapy/notes/save/${clientUuid}`;
                                if (currentNoteId) {
                                    url += `?noteId=${currentNoteId}`;
                                }

                                const response = await fetch(url, {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'text/plain;charset=UTF-8',
                                        [csrfHeader]: csrfToken
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

                // --- БЛОК 3: ЛОГІКА МОДАЛЬНОГО ВІКНА (TOGGLE) ---
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
                                const response = await fetch(`/therapy/notes/history/${clientUuid}`);
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

                // --- БЛОК 4: ВИМКНЕННЯ КІМНАТИ ПРИ ЗАКРИТТІ ---
    if (isProfessional) {
        window.addEventListener('beforeunload', () => {
            // ДОДАНО /therapy ПЕРЕД /room, щоб шлях став точним
            const url = '/therapy/room/' + clientUuid + '/leave';

            fetch(url, {
                method: 'POST',
                headers: {
                    [csrfHeader]: csrfToken,
                    'Content-Type': 'application/json'
                },
                keepalive: true // Дозволяє запиту завершитися після закриття вкладки
            });
        });
    }

});


//
//
//    // --- БЛОК 1: ІНІЦІАЛІЗАЦІЯ JITSI ---
//    const btnStartVideo = document.getElementById('start-video-btn');
//    // Кнопка існує тільки якщо є токен. Перевіряємо, чи фахівець натискає її.
//    if (btnStartVideo && (config.isAdmin || config.isTherapist)) {
//        btnStartVideo.addEventListener('click', () => {
//            const domain = "8x8.vc";
//            const options = {
//                roomName: "vpaas-magic-cookie-2eb0f0ca979c450b86558296a60455db/" + config.roomName,
//                width: '100%',
//                height: '100%',
//                parentNode: document.querySelector('#jitsi-container'),
//                jwt: config.jitsiJwt,
//                configOverwrite: {
//                    prejoinPageEnabled: false,
//                    startWithAudioMuted: false,
//                    startWithVideoMuted: false
//                },
//                interfaceConfigOverwrite: {
//                    TOOLBAR_BUTTONS: [
//                        'microphone', 'camera', 'closedcaptions', 'desktop', 'fullscreen',
//                        'fodeviceselection', 'hangup', 'profile', 'chat', 'recording',
//                        'livestreaming', 'etherpad', 'sharedvideo', 'settings', 'raisehand',
//                        'videoquality', 'filmstrip', 'invite', 'feedback', 'stats', 'shortcuts',
//                        'tileview', 'videobackgroundblur', 'download', 'help', 'mute-everyone', 'security'
//                    ]
//                }
//            };
//
//            document.querySelector('#jitsi-container').innerHTML = '';
//            const api = new JitsiMeetExternalAPI(domain, options);
//            btnStartVideo.style.display = 'none';
//        });
//    }
//
//    // --- БЛОК 2: АВТОЗБЕРЕЖЕННЯ НОТАТОК ---
//    if (config.isAdmin || config.isTherapist) {
//        const textarea = document.getElementById('session-notes');
//        const statusEl = document.getElementById('save-status');
//        const timeEl = document.getElementById('last-saved-time');
//        let saveTimeout;
//
//        if (textarea && config.lastNoteContent) {
//            textarea.value = config.lastNoteContent;
//        }
//
//        if (textarea) {
//            textarea.addEventListener('input', () => {
//                statusEl.textContent = 'Зміна...';
//                statusEl.className = 'status-indicator status-saving';
//                clearTimeout(saveTimeout);
//
//                saveTimeout = setTimeout(async () => {
//                    statusEl.textContent = 'Зберігаю...';
//
//                    try {
//                        const response = await fetch('/api/notes/save', {
//                            method: 'POST',
//                            headers: {
//                                'Content-Type': 'application/x-www-form-urlencoded',
//                                [config.csrfHeader]: config.csrfToken
//                            },
//                            body: `clientUuid=${config.clientUuid}&content=${encodeURIComponent(textarea.value)}`
//                        });
//
//                        if (response.ok) {
//                            statusEl.textContent = 'Збережено';
//                            statusEl.className = 'status-indicator status-saved';
//                            const now = new Date();
//                            timeEl.textContent = now.toLocaleTimeString('uk-UA');
//                        } else {
//                            throw new Error('Server error');
//                        }
//                    } catch (e) {
//                        statusEl.textContent = 'Помилка збереження';
//                        statusEl.className = 'status-indicator status-error';
//                    }
//                }, 1500);
//            });
//        }
//
//        // --- БЛОК 3: ІСТОРІЯ НОТАТОК ---
//        const btnShowHistory = document.getElementById('btn-show-history');
//        const modal = document.getElementById('history-modal');
//        const btnCloseModal = document.getElementById('btn-close-modal');
//        const historyList = document.getElementById('history-list');
//
//        if (btnShowHistory && modal) {
//            const toggleHistory = async () => {
//                modal.classList.add('is-visible');
//                historyList.innerHTML = '<p class="text-muted">Завантаження...</p>';
//
//                try {
//                    const response = await fetch(`/api/notes/history/${config.clientUuid}`);
//                    if (!response.ok) throw new Error('Помилка сервера');
//
//                    const notes = await response.json();
//
//                    if (notes.length === 0) {
//                        historyList.innerHTML = '<p class="text-muted">Історія порожня.</p>';
//                    } else {
//                        historyList.innerHTML = notes.map(n => `
//                            <div class="history-item">
//                                <span class="history-date">${new Date(n.createdAt).toLocaleString('uk-UA')}</span>
//                                <div class="history-text">${n.content}</div>
//                            </div>
//                        `).join('');
//                    }
//                } catch (e) {
//                    historyList.innerHTML = '<p class="status-error">Помилка завантаження історії.</p>';
//                }
//            };
//
//            btnShowHistory.addEventListener('click', toggleHistory);
//            btnCloseModal.addEventListener('click', () => modal.classList.remove('is-visible'));
//            modal.addEventListener('click', (e) => {
//                if (e.target === modal) modal.classList.remove('is-visible');
//            });
//        }
//
//        // --- БЛОК 4: ВИМКНЕННЯ КІМНАТИ ПРИ ЗАКРИТТІ ---
//        window.addEventListener('beforeunload', () => {
//            const url = '/therapy/room/' + config.clientUuid + '/leave';
//            fetch(url, {
//                method: 'POST',
//                headers: {
//                    [config.csrfHeader]: config.csrfToken,
//                    'Content-Type': 'application/json'
//                },
//                keepalive: true
//            });
//        });
//    }
//});