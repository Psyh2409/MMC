'use strict';

let stompClient = null;
let currentUser = null;
let currentParentId = null;
const PUBLIC_ID = '11111111-1111-1111-1111-111111111111';
let currentRecipientId = PUBLIC_ID;
let activeTab = 'public';

// Окремі трекери дат для кожної вкладки, щоб вони не перетиналися
let lastDatePublic = null;
let lastDatePrivate = null;

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
    const targetAreaId = type === 'public' ? 'chat-messages' : 'private-messages';
    showMessage(message, targetAreaId, type);

    if (type === 'private' && activeTab === 'public') {
        const badge = document.getElementById('private-badge');
        const privTab = document.getElementById('tab-private');

        if (badge) {
            badge.classList.remove('hidden');
            badge.innerText = parseInt(badge.innerText) + 1;
        }
        if (privTab) {
            privTab.classList.add('pulse-notification');
        }
    }
}

function loadPublicHistory() {
    fetch('/api/chat/history/public')
        .then(response => response.json())
        .then(messages => {
            const chatArea = document.getElementById('chat-messages');
            if (!chatArea) return;
            chatArea.innerHTML = '';
            lastDatePublic = null; // Скидаємо трекер перед завантаженням
            messages.forEach(msg => {
                showMessage(msg, 'chat-messages', 'public');
            });
            scrollToBottom('chat-messages');
        });
}

function loadPrivateHistory() {
    fetch('/api/chat/history/private')
        .then(response => response.json())
        .then(messages => {
            const privArea = document.getElementById('private-messages');
            if (!privArea) return;
            privArea.innerHTML = '';
            lastDatePrivate = null; // Скидаємо трекер перед завантаженням
            messages.forEach(msg => {
                showMessage(msg, 'private-messages', 'private');
            });
            scrollToBottom('private-messages');
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