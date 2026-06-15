document.addEventListener('DOMContentLoaded', () => {

    // --- 1. Отримуємо дані від бекенду через глобальний конфіг ---
    const config = window.TherapyConfig;
    if (!config) {
        console.error("Критична помилка: Конфігурація кімнати не завантажена.");
        return;
    }

    const appId = 'vpaas-magic-cookie-a6c49e33cd42404bb9c7e3d27f7825c6';

    // --- БЛОК 1: ІНІЦІАЛІЗАЦІЯ ВІДЕО JITSI ---
    let jitsiApi = null;
    const initJitsi = () => {
        const container = document.querySelector('#jitsi-container');
        if (!container) return;

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
        jitsiApi.addEventListener('videoConferenceJoined', () => {
            jitsiApi.executeCommand('toggleTileView');
        });
    };
    initJitsi();

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
                    let url = `/therapy/notes/save/${config.clientUuid}`;
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
                    const response = await fetch(`/therapy/notes/history/${config.clientUuid}`);
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
    if (config.isProfessional) {
        window.addEventListener('beforeunload', () => {
            const url = '/therapy/room/' + config.clientUuid + '/leave';
            fetch(url, {
                method: 'POST',
                headers: {
                    [config.csrfHeader]: config.csrfToken,
                    'Content-Type': 'application/json'
                },
                keepalive: true
            });
        });
    }
});