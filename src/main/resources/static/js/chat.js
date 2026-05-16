'use strict';

let stompClient = null;
let currentUser = null;
let currentParentId = null;
const PUBLIC_ID = '11111111-1111-1111-1111-111111111111';
let currentRecipientId = PUBLIC_ID;
let activeTab = 'public';
let lastDisplayedDate = null;

function scrollToBottom(elementId) {
    const el = document.getElementById(elementId);
    if (el && !el.classList.contains('hidden')) {
        requestAnimationFrame(() => {
            el.scrollTop = el.scrollHeight;
        });
    }
}

function checkAndDisplayDate(message, targetArea) {
    const messageDate = new Date(message.timestamp).toLocaleDateString('uk-UA', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
    });

    if (lastDisplayedDate !== messageDate) {
        const dateSeparator = document.createElement('div');
        dateSeparator.className = 'date-separator';
        dateSeparator.innerHTML = `<span>${messageDate}</span>`;
        targetArea.appendChild(dateSeparator);
        lastDisplayedDate = messageDate;
    }
}

function connect() {
    currentUser = document.querySelector('meta[name="current-user"]').content;
    let socket = new SockJS('/ws-chat');
    stompClient = Stomp.over(socket);

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

// 🟢 ФІКС ВКАДКАДКАХ: Тепер класи динамічно змінюються за нашою дизайн-системою
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
    showMessage(message, targetAreaId);

    if (type === 'private' && activeTab === 'public') {
        const badge = document.getElementById('private-badge');
        if (badge) {
            badge.classList.remove('hidden');
            badge.innerText = parseInt(badge.innerText) + 1;
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
            lastDisplayedDate = null;
            messages.forEach(msg => {
                checkAndDisplayDate(msg, chatArea);
                showMessage(msg, 'chat-messages');
            });
        });
}

function loadPrivateHistory() {
    fetch('/api/chat/history/private')
        .then(response => response.json())
        .then(messages => {
            const privArea = document.getElementById('private-messages');
            if (!privArea) return;
            privArea.innerHTML = '';
            lastDisplayedDate = null;
            messages.forEach(msg => {
                checkAndDisplayDate(msg, privArea);
                showMessage(msg, 'private-messages');
            });
        });
}

function prepareReply(messageId, senderName, type, senderUuid) {
    currentParentId = messageId;
    currentRecipientId = (type === 'private') ? senderUuid : PUBLIC_ID;

    if (type === 'private') {
        switchChat('private');
    }

    const replyPreview = document.getElementById('reply-preview');
    const replyToText = document.getElementById('reply-to-text');

    if (replyPreview && replyToText) {
        const modeText = type === 'private' ? '🔒 Приватна відповідь для ' : '💬 Публічна відповідь для ';
        replyToText.innerText = modeText + senderName;
        replyPreview.style.backgroundColor = type === 'private' ? 'var(--bg-elevated)' : 'var(--bg-main)';
        replyPreview.classList.remove('hidden');
    }
    document.getElementById('messageInput').focus();
}

function cancelReply() {
    currentParentId = null;
    const replyPreview = document.getElementById('reply-preview');
    if (replyPreview) replyPreview.classList.add('hidden');
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

function showMessage(message, targetId) {
    const chatArea = document.getElementById(targetId);
    if (!chatArea) return;

    const messageElement = document.createElement('div');
    messageElement.id = 'msg-' + message.id;

    const isMe = message.senderId === currentUser;
    messageElement.className = isMe ? 'message-box my-message' : 'message-box other-message';

    // 🟢 ЗАХИСТ ВІД ПАДІННЯ: Безпечний пошук нотаток без крашу всього чату
    let quoteHtml = '';
    if (message.parentId) {
        const parentElem = document.getElementById('msg-' + message.parentId);
        let textToQuote = "Повідомлення не знайдено";
        let authorToQuote = "Хтось";

        if (parentElem) {
            const textNode = parentElem.querySelector('.message-text');
            const authorNode = parentElem.querySelector('.message-meta strong');
            if (textNode) textToQuote = textNode.innerText;
            if (authorNode) authorToQuote = authorNode.innerText;
        }

        const shortText = textToQuote.length > 50 ? textToQuote.substring(0, 50) + '...' : textToQuote;
        quoteHtml = `
            <div class="quote-box">
                 <small class="quote-author">Відповідь для <b>${authorToQuote}</b>:</small>
                 <div class="quote-text">↳ <i>${shortText}</i></div>
            </div>`;
    }

    const senderName = message.senderName || 'Користувач';
    const displayName = isMe ? 'Ви' : senderName;
    const firstLetter = senderName.charAt(0).toUpperCase();

    messageElement.innerHTML = `
        ${quoteHtml}

        <div class="message-header" style="display: flex; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-xs);">
            <div class="avatar-circle">${firstLetter}</div>
            <div class="message-meta">
                <strong>${displayName}</strong>
            </div>
        </div>

        <div class="message-text">${message.content}</div>

        <div class="message-actions" style="display: flex; gap: var(--space-xs); margin-top: var(--space-sm); flex-wrap: wrap;">
            <button type="button" class="btn-outline btn-sm" onclick="prepareReply('${message.id}', '${senderName}', 'public')">
                Відповісти публічно
            </button>

            ${!isMe ? `
            <button type="button" class="btn-outline btn-sm"
                onclick="prepareReply('${message.id}', '${senderName}', 'private', '${message.senderId}')">
                Написати приватно
            </button>
            ` : ''}
        </div>
    `;

    chatArea.appendChild(messageElement);

    if (targetId === (activeTab === 'public' ? 'chat-messages' : 'private-messages')) {
        scrollToBottom(targetId);
    }
}

document.addEventListener('DOMContentLoaded', connect);
document.getElementById('messageForm').addEventListener('submit', sendMessage);
document.getElementById('messageInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        document.getElementById('messageForm').dispatchEvent(new Event('submit'));
    }
});