'use strict';

let stompClient = null;
let currentUser = null;
let currentParentId = null;
let currentRecipientId = 'PUBLIC'; // за замовчуванням
let activeTab = 'public'; // Слідкуємо, де зараз користувач
let lastDisplayedDate = null; // Змінна для відстеження дат

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

// В методах loadPublicHistory та loadPrivateHistory
// ПЕРЕД початком циклу скидай: lastDisplayedDate = null;
// ВСЕРЕДИНІ циклу перед showMessage викликай: checkAndDisplayDate(msg, chatArea);

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
        loadPrivateHistory(); // ТЕПЕР ВІН ВИКЛИКАЄТЬСЯ
    });
}

// Логіка перемикання вкладок
function switchChat(type) {
    activeTab = type;
    const pubArea = document.getElementById('chat-messages');
    const privArea = document.getElementById('private-messages');

    document.getElementById('tab-public').classList.toggle('active', type === 'public');
    document.getElementById('tab-private').classList.toggle('active', type === 'private');

    if (type === 'public') {
        pubArea.classList.remove('hidden');
        privArea.classList.add('hidden');
    } else {
        pubArea.classList.add('hidden');
        privArea.classList.remove('hidden');
        document.getElementById('private-badge').classList.add('hidden');
        document.getElementById('private-badge').innerText = '0';
    }
}

// Функція обробки вхідних повідомлень (вирішує куди класти і чи показувати бадж)
function handleIncomingMessage(message, type) {
    const targetAreaId = type === 'public' ? 'chat-messages' : 'private-messages';
    showMessage(message, targetAreaId);

    // Якщо прийшов приват, а ми на вкладці "Спільнота" — показуємо лічильник
    if (type === 'private' && activeTab === 'public') {
        const badge = document.getElementById('private-badge');
        badge.classList.remove('hidden');
        badge.innerText = parseInt(badge.innerText) + 1;
    }
}

function loadPublicHistory() {
    fetch('/api/chat/history/public')
        .then(response => response.json())
        .then(messages => {
            const chatArea = document.getElementById('chat-messages');
            chatArea.innerHTML = '';
            lastDisplayedDate = null; // Скидаємо дату перед початком історії
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
            privArea.innerHTML = '';
            lastDisplayedDate = null; // Скидаємо дату
            messages.forEach(msg => {
                checkAndDisplayDate(msg, privArea);
                showMessage(msg, 'private-messages');
            });
        });
}

function prepareReply(messageId, senderName, type, senderUuid) {
    currentParentId = messageId;
    currentRecipientId = (type === 'private') ? senderUuid : 'PUBLIC';
    // Якщо обрано приват — автоматично перемикаємо юзера на приватну вкладку
    if (type === 'private') {
        switchChat('private');
    }

    const replyPreview = document.getElementById('reply-preview');
    const replyToText = document.getElementById('reply-to-text');

    if (replyPreview && replyToText) {
        const modeText = type === 'private' ? '🔒 Приватна відповідь для ' : '💬 Публічна відповідь для ';
        replyToText.innerText = modeText + senderName;

        // Можна змінити колір плашки для привату, щоб юзер бачив різницю
        replyPreview.style.backgroundColor = type === 'private' ? '#e3f2fd' : '#f1f3f4';

        replyPreview.classList.remove('hidden');
    }
    document.getElementById('messageInput').focus();
}

function cancelReply() {
    currentParentId = null;
    const replyPreview = document.getElementById('reply-preview');
    if (replyPreview) replyPreview.classList.add('hidden');
}

// В sendMessage додаємо жорсткий фільтр
function sendMessage(event) {
    event.preventDefault();
    const messageInput = document.getElementById('messageInput');
    const messageContent = messageInput.value.trim();

    if (messageContent && stompClient) {
        let finalRecipient = currentRecipientId;
        let destination = "/app/chat";

        if (activeTab === 'private') {
            // Якщо в приваті і отримувач не вибраний (або залишився PUBLIC від минулого разу)
            if (!finalRecipient || finalRecipient === 'PUBLIC') {
                finalRecipient = currentUser; // Нотатка самому собі
            }
            destination = "/app/chat";
        } else {
            // У публічній вкладці завжди PUBLIC
            destination = "/app/chat.public";
            finalRecipient = 'PUBLIC';
        }

        const chatMessage = {
            senderId: currentUser,
            recipientId: finalRecipient,
            content: messageContent,
            parentId: currentParentId,
            status: 'SENT'
        };

        stompClient.send(destination, {}, JSON.stringify(chatMessage));

        // --- ПІСЛЯ ВІДПРАВКИ ---
        messageInput.value = '';
        currentRecipientId = 'PUBLIC'; // Скидаємо отримувача в дефолт
        currentParentId = null;        // Скидаємо батьківське повідомлення
        cancelReply();                 // Прибираємо візуальну плашку цитування
    }
}

function showMessage(message, targetId) {
    const chatArea = document.getElementById(targetId);
    const messageElement = document.createElement('div');
    messageElement.id = 'msg-' + message.id; // Для цитування

    const isMe = message.senderId === currentUser;
    messageElement.className = isMe ? 'message-box my-message' : 'message-box other-message';

    // РОЗУМНЕ ЦИТУВАННЯ
    let quoteHtml = '';
    if (message.parentId) {
        const parentElem = document.getElementById('msg-' + message.parentId);
        let textToQuote = "Повідомлення не знайдено";

        if (parentElem) {
            textToQuote = parentElem.querySelector('.message-text').innerText;
        }

        const shortText = textToQuote.length > 50 ? textToQuote.substring(0, 50) + '...' : textToQuote;
        quoteHtml = `<div class="quote-box">↳ <i>${shortText}</i></div>`;
    }

    messageElement.innerHTML = `
        ${quoteHtml}
        <div class="message-meta">
            <strong>${isMe ? 'Ви' : (message.senderName || message.senderId)}</strong>
        </div>
        <div class="message-text">${message.content}</div>

        <div class="message-actions">
            <button type="button" class="reply-btn" onclick="prepareReply('${message.id}', '${message.senderName || message.senderId}', 'public')">
                Відповісти публічно
            </button>

            ${!isMe ? `
            <button type="button" class="reply-btn private-btn"
                onclick="prepareReply('${message.id}', '${message.senderName || message.senderId}', 'private', '${message.senderId}')">
                Написати приватно
            </button>
            ` : ''}
        </div>
    `;

    chatArea.appendChild(messageElement);
    // Замість chatArea.scrollTop = chatArea.scrollHeight;
    chatArea.scrollTo({
        top: chatArea.scrollHeight,
        behavior: 'smooth'
    });
}

document.addEventListener('DOMContentLoaded', connect);
document.getElementById('messageForm').addEventListener('submit', sendMessage);
// Відправка по натисканню Enter (без Shift)
document.getElementById('messageInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault(); // Забороняємо перенос рядка
        document.getElementById('messageForm').dispatchEvent(new Event('submit'));
    }
});