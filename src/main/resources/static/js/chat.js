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
        });
    }

    const privArea = document.getElementById('private-messages');
    if (privArea) {
        privArea.addEventListener('scroll', () => {
            if (privArea.scrollTop === 0) loadMoreMessages();
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

function loadPublicHistory() {
    publicPage = 0;
    fetch(`/chat/${PUBLIC_ID}/messages?page=0`) // Тягнемо тільки перші 20 штук!
        .then(response => response.json())
        .then(messages => {
            // Сервер віддає найновіші першими. Перевертаємо для хронологічного порядку.
            publicMessages = messages.reverse();
            renderAllMessages('chat-messages', 'public', publicMessages);
            scrollToBottom('chat-messages');
        });
}

function loadPrivateHistory() {
    // Жодних заглушок! Стукаємо прямо в твій старий, надійний ендпоінт
    fetch('/api/chat/history/private')
        .then(response => response.json())
        .then(messages => {
            // Зберігаємо повідомлення в масив
            privateMessages = messages;

            // Відмальовуємо їх на екрані
            renderAllMessages('private-messages', 'private', privateMessages);

            // Прокручуємо вниз до останнього повідомлення
            scrollToBottom('private-messages');
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
    }

    const parentMessage = messageCache.get(messageId.toLowerCase());
    const snippet = parentMessage ? escapeHtml(parentMessage.content) : '';
    const shortSnippet = snippet.length > 60 ? snippet.substring(0, 60) + '...' : snippet;

    const replyPreview = document.getElementById('reply-preview');
    const replyToText = document.getElementById('reply-to-text');

    replyToText.innerHTML = `Вам відповідь для <strong>${escapeHtml(senderName)}</strong> на: <span class="preview-text-snippet">"${shortSnippet}"</span>`;
    replyPreview.classList.remove('hidden');

    document.getElementById('messageInput').focus();
}

function cancelReply() {
    currentParentId = null;
    const replyPreview = document.getElementById('reply-preview');
    if (replyPreview) replyPreview.classList.add('hidden');
}

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

    if (messageContent && stompClient) {
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

// ДОДАНО ПАРАМЕТР type (public/private) ДЛЯ ПРАВИЛЬНИХ ДАТ
function showMessage(message, targetId, chatType) {
    if (!message || !message.id) return;

    // Нормалізуємо UUID, щоб кеш працював залізобетонно
    messageCache.set(message.id.toLowerCase(), message);

    const chatArea = document.getElementById(targetId);
    if (!chatArea) return;

    // Передаємо chatType для правильної дати
    checkAndDisplayDate(message, chatArea, chatType);

    const messageElement = document.createElement('div');
    // НАДАЄМО ID ДЛЯ ВИДАЛЕННЯ
    messageElement.id = `msg-item-${message.id}`;

    const isMe = message.senderId && currentUser &&
                 (message.senderId.toString().toLowerCase() === currentUser.toString().toLowerCase());

    messageElement.className = `message-item ${isMe ? 'message-me' : 'message-other'}`;

    const displayName = isMe ? 'Я' : escapeHtml(message.senderName);

    const hasAvatar = message.senderAvatar && message.senderAvatar.trim().length > 0;
    const avatarHtml = hasAvatar
        ? `<img src="/api/media/${escapeHtml(message.senderAvatar)}" alt="Avatar">`
        : displayName.charAt(0).toUpperCase();

    let replyContextHtml = '';
        if (message.parentId) {
            // Завжди шукаємо в нижньому регістрі!
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
                        <blockquote class="reply-quote" style="opacity: 0.6;">[Повідомлення недоступне в поточній сесії]</blockquote>
                    </div>
                `;
            }
        }

    const safeContent = escapeHtml(message.content);

    // ВІЗУАЛЬНИЙ БЛОК ДІЙ (ТУТ ДОДАНО КНОПКУ ВИДАЛЕННЯ ДЛЯ ВЛАСНИХ ПОВІДОМЛЕНЬ)
    messageElement.innerHTML = `
            <div class="message-header">
                <div class="avatar-circle avatar-xs">${avatarHtml}</div>
                <div class="message-meta">
                    <strong>${displayName}</strong>
                </div>
            </div>

            ${replyContextHtml}

            <div class="message-text">${safeContent}</div>

            <div class="message-actions" style="display: flex; gap: var(--space-xs); margin-top: var(--space-sm); flex-wrap: wrap;">
                            <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'public')">
                                Відповісти публічно
                            </button>
                            ${!isMe ? `
                            <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${escapeHtml(message.senderName)}', 'private', '${message.senderId}')">
                                Написати приватно
                            </button>
                            ` : `
                            <button type="button" class="btn-outline btn-sm" onclick="deleteMessage('${message.id}')" title="Видалити повідомлення">
                                Видалити
                            </button>
                            `}
                        </div>
        `;

    chatArea.appendChild(messageElement);
}

document.addEventListener('DOMContentLoaded', connect);
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
}

// Автоматичне підхоплення медіа-фасадів для чату
const chatObserver = new MutationObserver((mutations) => {
    if (typeof window.applyMediaFacades === 'function') {
        window.applyMediaFacades();
    }
});

// Вішаємо спостерігача на обидві вкладки чату
document.addEventListener('DOMContentLoaded', () => {
    const pubArea = document.getElementById('chat-messages');
    const privArea = document.getElementById('private-messages');

    if (pubArea) chatObserver.observe(pubArea, { childList: true, subtree: true });
    if (privArea) chatObserver.observe(privArea, { childList: true, subtree: true });
});