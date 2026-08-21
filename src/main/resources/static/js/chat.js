'use strict';

let stompClient = null;
let currentUser = null;
let currentParentId = null;
const PUBLIC_ID = '11111111-1111-1111-1111-111111111111';
let currentRecipientId = PUBLIC_ID;
let activeTab = 'public';
let publicPage = 0;
let privatePage = 0;
let isChatLoading = false;
// Окремі трекери дат для кожної вкладки, щоб вони не перетиналися
let lastDatePublic = null;
let lastDatePrivate = null;
let publicMessages = [];
let privateMessages = [];
// НАДІЙНИЙ КЕШ ДЛЯ ЗБЕРЕЖЕННЯ КОНТЕКСТУ ПОВІДОМЛЕНЬ
// Перевірка прав адміністратора
const adminMeta = document.querySelector('meta[name="is-admin"]');
const isAdmin = adminMeta && adminMeta.content === 'true';
const messageCache = new Map();

// Утиліта для захисту від XSS атак (екранування HTML-тегів у тексті)
function escapeHtml(unsafe) {
    if (!unsafe) return '';
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

function scrollToBottom(elementId) {
    const el = document.getElementById(elementId);
    if (el && !el.classList.contains('hidden')) {
        requestAnimationFrame(() => {
            el.scrollTop = el.scrollHeight;
        });
    }
}

function checkAndDisplayDate(message, targetArea, type) {
    const messageDate = new Date(message.timestamp).toLocaleDateString('uk-UA', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
    });

    const lastDate = (type === 'public') ? lastDatePublic : lastDatePrivate;

    if (lastDate !== messageDate) {
        const dateSeparator = document.createElement('div');
        dateSeparator.className = 'date-separator';
        dateSeparator.innerHTML = `<span>${messageDate}</span>`;
        targetArea.appendChild(dateSeparator);

        if (type === 'public') {
            lastDatePublic = messageDate;
        } else {
            lastDatePrivate = messageDate;
        }
    }
}

function connect() {
    const userMeta = document.querySelector('meta[name="current-user"]');
    if (!userMeta) return; // Захист, якщо ми не на сторінці чату
    currentUser = userMeta.content;

    let socket = new SockJS('/ws-chat');
    stompClient = Stomp.over(socket);
    // Вимикаємо зайвий спам від Stomp у консоль
    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        stompClient.subscribe('/topic/public', function (message) {
            handleIncomingMessage(JSON.parse(message.body), 'public');
        });

        stompClient.subscribe('/user/queue/messages', function (message) {
            handleIncomingMessage(JSON.parse(message.body), 'private');
        });

        loadPublicHistory();
        loadPrivateHistory();
    });
    // Додати в кінець функції connect(), перед закриваючою дужкою }
    const pubArea = document.getElementById('chat-messages');
    if (pubArea) {
        pubArea.addEventListener('scroll', () => {
            if (pubArea.scrollTop === 0) loadMoreMessages();
            checkScrollPosition('chat-messages'); // <--- ДОДАНО: відстеження для кнопки
        });
    }

    const privArea = document.getElementById('private-messages');
    if (privArea) {
        privArea.addEventListener('scroll', () => {
            if (privArea.scrollTop === 0) loadMoreMessages();
            checkScrollPosition('private-messages'); // <--- ДОДАНО: відстеження для привату
        });
    }
}

function switchChat(type) {
    activeTab = type;
    const pubArea = document.getElementById('chat-messages');
    const privArea = document.getElementById('private-messages');
    const pubTab = document.getElementById('tab-public');
    const privTab = document.getElementById('tab-private');

    if (type === 'public') {
        pubArea.classList.remove('hidden');
        privArea.classList.add('hidden');

        pubTab.className = 'btn-primary';
        privTab.className = 'btn-outline';

        scrollToBottom('chat-messages');
    } else {
        pubArea.classList.add('hidden');
        privArea.classList.remove('hidden');

        pubTab.className = 'btn-outline';
        privTab.className = 'btn-primary';

        privTab.classList.remove('pulse-notification');

        scrollToBottom('private-messages');

        const badge = document.getElementById('private-badge');
        if (badge) {
            badge.classList.add('hidden');
            badge.innerText = '0';
        }
    }
}

function handleIncomingMessage(message, type) {
    // 1. Зберігаємо повідомлення в масив (для правильної роботи історії)
    if (type === 'public') {
        publicMessages.push(message);
    } else {
        privateMessages.push(message);
    }

    // 2. Виводимо бульбашку повідомлення на екран
    const targetAreaId = type === 'public' ? 'chat-messages' : 'private-messages';
    showMessage(message, targetAreaId, type);

    // 3. ВІДНОВЛЕНА СИГНАЛІЗАЦІЯ (БЛИМАННЯ ТА ЦИФРА)
    if (type === 'private' && activeTab === 'public') {
        const badge = document.getElementById('private-badge');
        if (badge) {
            badge.classList.remove('hidden'); // Знімаємо приховування (твоє CSS блимання знову запрацює)
            let currentCount = parseInt(badge.innerText) || 0;
            badge.innerText = currentCount + 1; // Збільшуємо циферку на +1
        }
    }
}

// ==========================================
// 1. ПУБЛІЧНИЙ ЧАТ (Із розрахунком сторінки)
// ==========================================
function loadPublicHistory() {
    const hash = window.location.hash;
    let targetId = null;

    if (hash && hash.startsWith('#msg-item-')) {
        targetId = hash.replace('#msg-item-', '');
    }

    // Якщо є конкретне повідомлення — дізнаємося його сторінку на бекенді
    if (targetId) {
        fetch(`/api/chat/messages/${targetId}/page?chatRoomId=${PUBLIC_ID}`)
            .then(res => res.ok ? res.json() : 0)
            .then(targetPage => {
                fetchPublicMessagesUpToPage(targetPage);
            })
            .catch(error => {
                console.error('Помилка визначення сторінки публічного повідомлення:', error);
                fetchPublicMessagesUpToPage(0);
            });
    } else {
        fetchPublicMessagesUpToPage(0);
    }
}

function fetchPublicMessagesUpToPage(page) {
    publicPage = page;
    fetch(`/chat/${PUBLIC_ID}/messages?page=${page}`)
        .then(response => {
            if (!response.ok) throw new Error('Помилка мережі при завантаженні публічного чату');
            return response.json();
        })
        .then(messages => {
            publicMessages = messages.reverse();
            renderAllMessages('chat-messages', 'public', publicMessages);

            // Якщо якір є на сторінці — скролимо до нього, інакше — в самий кінець
            if (!scrollToTargetMessage()) {
                scrollToBottom('chat-messages');
            }

            // Одразу перевіряємо стан кнопка після рендерингу
            checkScrollPosition('chat-messages');
        })
        .catch(error => {
            console.error('Помилка завантаження публічної історії:', error);
        });
}

// ==========================================
// 2. ПРИВАТНИЙ ЧАТ (Пряме завантаження всієї історії)
// ==========================================
function loadPrivateHistory() {
    privatePage = 0; // Скидаємо лічильник сторінок для приватної історії

    fetch('/api/chat/history/private')
        .then(response => {
            if (!response.ok) throw new Error('Помилка мережі при завантаженні приватної історії');
            return response.json();
        })
        .then(messages => {
            privateMessages = messages;
            renderAllMessages('private-messages', 'private', privateMessages);

            // Оскільки всі приватні повідомлення вже в DOM, якір знайдеться миттєво
            if (!scrollToTargetMessage()) {
                scrollToBottom('private-messages');
            }
        })
        .catch(error => {
            console.error('Помилка завантаження приватної історії:', error);
        });
}

function prepareReply(messageId, senderName, type, recipientId) {
    currentParentId = messageId;

    if (type === 'private' && recipientId) {
        currentRecipientId = recipientId;
        switchChat('private');
    } else {
        currentRecipientId = PUBLIC_ID;
        switchChat('public'); // Перемикаємо контекст і вкладку на публічну
    }

    const parentMessage = messageCache.get(messageId.toLowerCase());
    const snippet = parentMessage ? escapeHtml(parentMessage.content) : '';
    const shortSnippet = snippet.length > 60 ? snippet.substring(0, 60) + '...' : snippet;

    const replyPreview = document.getElementById('reply-preview');
    const replyToText = document.getElementById('reply-to-text');

    replyToText.innerHTML = `Вам відповідь для <strong>${escapeHtml(senderName)}</strong> на: <span class="preview-text-snippet">"${shortSnippet}"</span>`;
    if (replyPreview) replyPreview.classList.remove('hidden');

    document.getElementById('messageInput').focus();
}

    let editingMessageId = null;

    function prepareEditMessage(messageId) {
        const message = messageCache.get(messageId.toLowerCase());
        if (!message) return;

        editingMessageId = messageId;
        const messageInput = document.getElementById('messageInput');
        if (!messageInput) return;

        messageInput.value = message.content;
        messageInput.focus();

        // Використовуємо плашку прев'ю, яка у вас вже є
        const replyPreview = document.getElementById('reply-preview');
        const replyToText = document.getElementById('reply-to-text');
        if (replyPreview && replyToText) {
            replyToText.innerHTML = `✏️ <strong>Редагування повідомлення</strong>`;
            replyPreview.classList.remove('hidden');
        }
    }

//    function cancelEdit() {
//        editingMessageId = null;
//        cancelReply(); // ховаємо плашку і скидаємо parentId
//    }
//
//function cancelReply() {
//    currentParentId = null;
//    const replyPreview = document.getElementById('reply-preview');
//    if (replyPreview) replyPreview.classList.add('hidden');
//}

// НОВА ФУНКЦІЯ: ВИДАЛЕННЯ ПОВІДОМЛЕННЯ
function deleteMessage(messageId) {
    if (!confirm('Ви впевнені, що хочете видалити це повідомлення? Цю дію неможливо скасувати.')) {
        return;
    }

    // Витягуємо CSRF токен з мета-тегів сторінки для безпеки Spring
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }

    fetch(`/api/chat/messages/${messageId}`, {
        method: 'DELETE',
        headers: headers
    })
    .then(response => {
        if (response.ok) {
            // 1. Прибираємо повідомлення з екрана миттєво
            const msgElement = document.getElementById(`msg-item-${messageId}`);
            if (msgElement) msgElement.remove();

            // 2. Очищаємо з кешу, щоб у цитатах писало "Видалено"
            messageCache.delete(messageId);
        } else {
            alert('Помилка при видаленні повідомлення. Спробуйте оновити сторінку.');
        }
    })
    .catch(error => console.error('Помилка видалення:', error));
}

function sendMessage(event) {
    event.preventDefault();
    const messageInput = document.getElementById('messageInput');
    if (!messageInput) return;
    const messageContent = messageInput.value.trim();

    if (!messageContent) return;

    // РЕЖИМ РЕДАГУВАННЯ: Якщо редагуємо існуюче повідомлення
    if (editingMessageId) {
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        fetch(`/api/chat/messages/${editingMessageId}`, {
            method: 'PUT',
            headers: headers,
            body: JSON.stringify({ content: messageContent })
        })
        .then(response => {
            if (!response.ok) throw new Error('Помилка оновлення');
            return response.json();
        })
        .then(updatedMsg => {
            // 1. Оновлюємо текст у DOM
            const msgElement = document.getElementById(`msg-item-${editingMessageId}`);
            if (msgElement) {
                const textEl = msgElement.querySelector('.message-text');
                if (textEl) textEl.innerText = updatedMsg.content;
            }

            // 2. Оновлюємо кеш
            messageCache.set(editingMessageId.toLowerCase(), updatedMsg);

            // 3. Скидаємо стан
            messageInput.value = '';
            cancelEdit();
        })
        .catch(error => console.error('Помилка редагування:', error));

        return; // Виходимо, щоб не відправляти нове повідомлення
    }

    // СТАНДАРТНА ВІДПРАВКА НОВОГО ПОВІДОМЛЕННЯ (твій існуючий код)
    if (stompClient) {
        let finalRecipient = currentRecipientId;
        let destination = "/app/chat";

        if (activeTab === 'private') {
            if (!finalRecipient || finalRecipient === PUBLIC_ID) {
                finalRecipient = currentUser;
            }
            destination = "/app/chat";
        } else {
            destination = "/app/chat.public";
            finalRecipient = PUBLIC_ID;
        }

        const chatMessage = {
            senderId: currentUser,
            recipientId: finalRecipient,
            content: messageContent,
            parentId: currentParentId,
            status: 'SENT'
        };

        stompClient.send(destination, {}, JSON.stringify(chatMessage));

        messageInput.value = '';
        currentRecipientId = PUBLIC_ID;
        currentParentId = null;
        cancelReply();
    }
}

function showMessage(message, targetId, chatType) {
    if (!message || !message.id) return;

    messageCache.set(message.id.toLowerCase(), message);

    const chatArea = document.getElementById(targetId);
    if (!chatArea) return;

    checkAndDisplayDate(message, chatArea, chatType);

    const messageElement = document.createElement('div');
    messageElement.id = `msg-item-${message.id}`;

    const isMe = message.senderId && currentUser &&
                 (message.senderId.toString().toLowerCase() === currentUser.toString().toLowerCase());

    messageElement.className = `message-item ${isMe ? 'message-me' : 'message-other'}`;

    const displayName = isMe ? 'Я' : escapeHtml(message.senderName);

    const hasAvatar = message.senderAvatar && message.senderAvatar.trim().length > 0;
    const avatarHtml = hasAvatar
        ? `<img src="/api/media/${escapeHtml(message.senderAvatar)}" alt="Avatar">`
        : displayName.charAt(0).toUpperCase();

    // 1. Блок контексту відповіді
    let replyContextHtml = '';
    if (message.parentId) {
        const parentMessage = messageCache.get(message.parentId.toLowerCase());
        if (parentMessage) {
            const parentSender = (parentMessage.senderId && currentUser && (parentMessage.senderId.toString().toLowerCase() === currentUser.toString().toLowerCase())) ? 'Я' : escapeHtml(parentMessage.senderName);
            const cleanText = escapeHtml(parentMessage.content);
            const shortText = cleanText.length > 50 ? cleanText.substring(0, 50) + '...' : cleanText;

            replyContextHtml = `
                <div class="message-reply-context">
                    <small>💬 У відповідь для <strong class="context-author">${parentSender}</strong>:</small>
                    <blockquote class="reply-quote">${shortText}</blockquote>
                </div>
            `;
        } else {
            replyContextHtml = `
                <div class="message-reply-context">
                    <small>💬 Відповідь на повідомлення:</small>
                    <blockquote class="reply-quote">[Повідомлення недоступне в поточній сесії]</blockquote>
                </div>
            `;
        }
    }

    // 2. Іконка редагування (Тільки у шапці СВОЇХ повідомлень)
    const editIconHtml = isMe ? `
        <button type="button" class="btn-icon-edit" onclick="prepareEditMessage('${message.id}')" title="Редагувати">
            <svg viewBox="0 0 24 24">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
            </svg>
        </button>
    ` : '';

    // 3. SVG-іконка кошика у шапці: ТІЛЬКИ для ЧУЖИХ повідомлень І ТІЛЬКИ для Адміністратора в публічному чаті
    const headerDeleteSvgHtml = (!isMe && chatType === 'public' && typeof isAdmin !== 'undefined' && isAdmin) ? `
        <button type="button" class="btn-icon-delete" onclick="deleteMessage('${message.id}')" title="Видалити як адміністратор">
            <svg viewBox="0 0 24 24">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
        </button>
    ` : '';

    // === ДОДАНО ТУТ: 3.1. SVG-іконка прапорця скарги (ТІЛЬКИ для ЧУЖИХ повідомлень) ===
    const reportSvgHtml = !isMe ? `
        <button type="button" class="btn-icon-delete" onclick="openReportModal('${message.id}', 'CHAT_MESSAGE')" title="Поскаржитися на повідомлення">
            <svg viewBox="0 0 24 24">
                <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"></path>
                <line x1="4" y1="22" x2="4" y2="15"></line>
            </svg>
        </button>
    ` : '';

    // 4. Текстова кнопка видалення знизу: ТІЛЬКИ для СВОЇХ повідомлень
    const textDeleteBtnHtml = isMe ? `
        <button type="button" class="btn-outline btn-sm" onclick="deleteMessage('${message.id}')" title="Видалити повідомлення">
            Видалити
        </button>
    ` : '';

    // 5. Формування кнопок дій під повідомленням
    let actionsHtml = '';
    if (chatType === 'private') {
        if (isMe) {
            actionsHtml = textDeleteBtnHtml;
        } else {
            actionsHtml = `
                <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'private', '${message.senderId}')">
                    Відповісти
                </button>
            `;
        }
    } else {
        if (isMe) {
            actionsHtml = `
                <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'public')">
                    Відповісти
                </button>
                ${textDeleteBtnHtml}
            `;
        } else {
            actionsHtml = `
                <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'public')">
                    Відповісти публічно
                </button>
                <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'private', '${message.senderId}')">
                    Написати приватно
                </button>
            `;
        }
    }

    // Перевірка на Soft Delete адміністратором
        const isDeletedByAdmin = message.deletedByAdmin || message.isDeletedByAdmin;

        // Якщо повідомлення видалено адміном — виводимо системну плашку, інакше — звичайний текст
        const safeContent = isDeletedByAdmin
            ? `<span class="text-muted font-italic">🚫 Повідомлення видалено адміністратором</span>`
            : escapeHtml(message.content);

        // Якщо повідомлення видалено, приховуємо кнопки дій (редагування, відповіді)
        if (isDeletedByAdmin) {
            actionsHtml = '';
        }

    // 6. Фінальний HTML картки
    // === ЗМІНЕНО ТУТ: у блок message-header-actions додано ${reportSvgHtml} ===
    messageElement.innerHTML = `
        <div class="message-header">
            <div class="message-author-info">
                <div class="avatar-circle avatar-xs">${avatarHtml}</div>
                <div class="message-meta">
                    <strong>${displayName}</strong>
                </div>
            </div>
            <div class="message-header-actions">
                ${editIconHtml}
                ${headerDeleteSvgHtml}
                ${reportSvgHtml}
            </div>
        </div>

        ${replyContextHtml}

        <div class="message-text">${safeContent}</div>

        <!-- Горизонтальна панель реакцій для чату -->
                <div class="message-reactions-bar mt-xs mb-xs">
                    <div class="reaction-wrapper d-inline-flex align-center gap-xs">
                        <button type="button" class="btn-outline btn-sm p-xs reaction-toggle-btn d-inline-flex align-center gap-xs"
                                data-target-type="CHAT_MESSAGE"
                                data-target-id="${message.id}"
                                onclick="toggleReactionDropdown(this)">
                            <span class="reaction-current-icon">🤍</span>
                            <span class="reaction-count text-xs fw-bold">${message.supportCount || 0}</span>
                        </button>

                        <div class="reaction-dropdown is-hidden card-surface p-xs d-inline-flex align-center gap-xs border-radius-sm shadow-sm">
                            <button type="button" class="btn-icon p-xs" data-reaction="AGREE" onclick="applyReaction(this)" title="Згоден">👍</button>
                            <button type="button" class="btn-icon p-xs" data-reaction="GRATITUDE" onclick="applyReaction(this)" title="Вдячність">🙏</button>
                            <button type="button" class="btn-icon p-xs" data-reaction="INSIGHT" onclick="applyReaction(this)" title="Цінно">💡</button>
                            <button type="button" class="btn-icon p-xs" data-reaction="EMPATHY" onclick="applyReaction(this)" title="Співчуття">💛</button>
                            <button type="button" class="btn-icon p-xs" data-reaction="SUPPORT" onclick="applyReaction(this)" title="Підтримка">🫂</button>
                        </div>
                    </div>
                </div>

        <div class="message-actions">
            ${actionsHtml}
        </div>
    `;

    chatArea.appendChild(messageElement);
}

document.getElementById('messageForm').addEventListener('submit', sendMessage);
document.getElementById('messageInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        document.getElementById('messageForm').dispatchEvent(new Event('submit'));
    }
});

// ЗАМІНИ loadMoreMessages на цей варіант:
function loadMoreMessages() {
    if (activeTab === 'private') return;
    if (isChatLoading) return;

    const chatAreaId = activeTab === 'public' ? 'chat-messages' : 'private-messages';
    const chatElement = document.getElementById(chatAreaId);
    if (!chatElement) return;

    const loaderId = 'chat-history-loader';
    if (!document.getElementById(loaderId)) {
        const loader = document.createElement('div');
        loader.id = loaderId;
        loader.style.padding = 'var(--space-md)';
        loader.style.textAlign = 'center';
        loader.style.color = 'var(--color-primary)';
        loader.style.fontWeight = 'bold';
        loader.innerText = '⏳ Завантаження старої історії...';
        chatElement.insertBefore(loader, chatElement.firstChild);
    }

    const oldScrollHeight = chatElement.scrollHeight;
    let nextPage = activeTab === 'public' ? publicPage + 1 : privatePage + 1;
    let roomId = activeTab === 'public' ? PUBLIC_ID : currentRecipientId;

    isChatLoading = true;

    fetch(`/chat/${roomId}/messages?page=${nextPage}`)
        .then(response => response.json()) // Отримуємо JSON
        .then(messages => {
            const loader = document.getElementById(loaderId);
            if (loader) loader.remove();

            if (messages.length > 0) {
                const olderMessages = messages.reverse();

                // Додаємо старі повідомлення НА ПОЧАТОК масиву і перемальовуємо
                if (activeTab === 'public') {
                    publicMessages = [...olderMessages, ...publicMessages];
                    publicPage++;
                    renderAllMessages(chatAreaId, 'public', publicMessages);
                } else {
                    privateMessages = [...olderMessages, ...privateMessages];
                    privatePage++;
                    renderAllMessages(chatAreaId, 'private', privateMessages);
                }

                // Повертаємо скрол рівно на те повідомлення, яке ти читав
                chatElement.scrollTop = chatElement.scrollHeight - oldScrollHeight;
            } else {
                const endNode = document.createElement('div');
                endNode.style.padding = 'var(--space-sm)';
                endNode.style.textAlign = 'center';
                endNode.style.color = 'var(--color-text-muted)';
                endNode.innerText = '✨ Вся історія завантажена';
                chatElement.insertBefore(endNode, chatElement.firstChild);
                setTimeout(() => endNode.remove(), 2000);
            }
            isChatLoading = false;
        })
        .catch(error => {
            console.error('Помилка:', error);
            const loader = document.getElementById(loaderId);
            if (loader) loader.remove();
            isChatLoading = false;
        });
}

function renderAllMessages(targetId, chatType, messageArray) {
    const chatArea = document.getElementById(targetId);
    if (!chatArea) return;

    chatArea.innerHTML = ''; // Очищаємо екран

    // Скидаємо трекери дат, щоб вони розставились коректно
    if (chatType === 'public') lastDatePublic = null;
    else lastDatePrivate = null;

    messageArray.forEach(msg => {
        showMessage(msg, targetId, chatType);
    });

    if (typeof loadReactions === 'function') loadReactions();
}

// Автоматичне підхоплення медіа-фасадів для чату
const chatObserver = new MutationObserver((mutations) => {
    if (typeof window.applyMediaFacades === 'function') {
        window.applyMediaFacades();
    }
});

// НОВА НАДІЙНА ФУНКЦІЯ ІНІЦІАЛІЗАЦІЇ СТАНУ
function initChatState() {
    const tabMeta = document.querySelector('meta[name="chat-active-tab"]');
    const recipientMeta = document.querySelector('meta[name="chat-active-recipient"]');

    if (tabMeta && tabMeta.content === 'private') {
        if (recipientMeta && recipientMeta.content) {
            currentRecipientId = recipientMeta.content;
        }
        // Миттєво перемикаємо на приватну вкладку до завантаження повідомлень
        switchChat('private');
    }
}

// Автоматична прокрутка до конкретного повідомлення за наявності якоря в URL
function scrollToTargetMessage() {
    const hash = window.location.hash;
    if (hash && hash.startsWith('#msg-item-')) {
        const targetEl = document.querySelector(hash);
        if (targetEl) {
            requestAnimationFrame(() => {
                targetEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            });
            return true; // Успішно знайшли і проскролили
        }
    }
    return false; // Якір відсутній або елемент ще не завантажився
}

// Перевірка позиції скролу та номера сторінки
function checkScrollPosition(chatAreaId) {
    const chatElement = document.getElementById(chatAreaId);
    const btn = document.getElementById('scroll-bottom-btn');
    if (!chatElement || !btn) return;

    const distanceFromBottom = chatElement.scrollHeight - chatElement.scrollTop - chatElement.clientHeight;

    const isScrolledUp = distanceFromBottom > 150;
    const isOldPage = (activeTab === 'public' && publicPage > 0);

    // Показуємо кнопку, якщо відскролили вгору АБО знаходимося на старій сторінці історії
    if (isScrolledUp || isOldPage) {
        btn.classList.remove('hidden');
    } else {
        btn.classList.add('hidden');
    }
}

// Стрибок до найновіших повідомлень (Миттєвий 1-й клік)
function jumpToLatestMessages() {
    const chatAreaId = activeTab === 'public' ? 'chat-messages' : 'private-messages';

    // 1. Обов'язково скидаємо якір з URL, щоб не триматися за старе повідомлення
    if (window.location.hash) {
        window.history.replaceState({}, document.title, window.location.pathname + window.location.search);
    }

    // 2. Якщо ми на старій сторінці — завантажуємо 0-у сторінку й опускаємо вниз
    if (activeTab === 'public' && publicPage !== 0) {
        loadPublicHistory();
    } else {
        // Якщо на 0-й сторінці просто відскролили вгору — плавно скролимо вниз
        scrollToBottom(chatAreaId);
    }

    const btn = document.getElementById('scroll-bottom-btn');
    if (btn) btn.classList.add('hidden');
}

// ЄДИНИЙ БЛОК ІНІЦІАЛІЗАЦІЇ ДОКУМЕНТА (Без таймерів і милиць)
document.addEventListener('DOMContentLoaded', () => {
    // 1. Одразу перемикаємо вкладку, якщо сервер наказав це зробити
    initChatState();

    // 2. Підключаємо сокети і тягнемо історію
    connect();

    // 3. Безпечно вішаємо обробники подій на форму (захист від NullPointerException)
    const form = document.getElementById('messageForm');
    const input = document.getElementById('messageInput');

    if (form) {
        form.addEventListener('submit', sendMessage);
    }

    if (input) {
        input.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (form) form.dispatchEvent(new Event('submit'));
            }
        });
    }

    // 4. Підключення медіа-фасадів (якщо вони є)
    const pubArea = document.getElementById('chat-messages');
    const privArea = document.getElementById('private-messages');
    if (pubArea) chatObserver.observe(pubArea, { childList: true, subtree: true });
    if (privArea) chatObserver.observe(privArea, { childList: true, subtree: true });
});

// Єдина функція повного скасування будь-якого стану (відповіді або редагування)
function cancelAction() {
    currentParentId = null;
    editingMessageId = null;

    const replyPreview = document.getElementById('reply-preview');
    if (replyPreview) {
        replyPreview.classList.add('hidden');
    }

    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.value = '';
    }
}

// Аліаси для зворотної сумісності
function cancelReply() {
    cancelAction();
}

function cancelEdit() {
    cancelAction();
}

// Відкрити/сховати панель реакцій
function toggleReactionDropdown(btn) {
    // Спочатку ховаємо всі інші відкриті панелі
    document.querySelectorAll('.reaction-dropdown').forEach(drop => {
        if (drop !== btn.nextElementSibling) drop.classList.add('is-hidden');
    });

    const dropdown = btn.nextElementSibling;
    if (dropdown) {
        dropdown.classList.toggle('is-hidden');
    }
}

// Застосувати обрану реакцію (для чату)
function applyReaction(btn) {
    const dropdown = btn.closest('.reaction-dropdown');
    const wrapper = btn.closest('.reaction-wrapper');
    const mainBtn = wrapper.querySelector('.reaction-toggle-btn');
    const countSpan = mainBtn.querySelector('.reaction-count');

    const targetType = mainBtn.getAttribute('data-target-type');
    const targetId = mainBtn.getAttribute('data-target-id');
    const reactionType = btn.getAttribute('data-reaction');
    const newIconHtml = btn.innerHTML;

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

    fetch('/api/reactions/toggle', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({ targetType, targetId, reactionType })
    })
    .then(response => {
        if (response.ok) {
            const iconContainer = mainBtn.querySelector('.reaction-current-icon');
            if (iconContainer) iconContainer.innerHTML = newIconHtml;

            if (!mainBtn.classList.contains('border-accent')) {
                mainBtn.classList.add('border-accent');
                let currentCount = parseInt(countSpan.innerText) || 0;
                countSpan.innerText = currentCount + 1;
            }

            dropdown.classList.add('is-hidden');
        }
    })
    .catch(error => console.error('Помилка збереження:', error));
}

// Автоматичне закриття панелі реакцій при кліку поза її межами
document.addEventListener('click', (e) => {
    if (!e.target.closest('.reaction-wrapper')) {
        document.querySelectorAll('.reaction-dropdown').forEach(drop => {
            drop.classList.add('is-hidden');
        });
    }
});