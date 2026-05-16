'use strict';

let stompClient = null;
let currentUser = null; // Тепер тут буде UUID
let currentParentId = null;
// Нова константа для публічного чату
const PUBLIC_ID = '11111111-1111-1111-1111-111111111111';
let currentRecipientId = PUBLIC_ID;
let activeTab = 'public';
let lastDisplayedDate = null;

// НОВА ФУНКЦІЯ: Розумна прокрутка
function scrollToBottom(elementId) {
    const el = document.getElementById(elementId);
    // Прокручуємо ТІЛЬКИ якщо елемент існує і НЕ прихований
    if (el && !el.classList.contains('hidden')) {
        // requestAnimationFrame - це правильний спосіб дочекатися відмальовки DOM замість setTimeout
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

function switchChat(type) {
    activeTab = type;
    const pubArea = document.getElementById('chat-messages');
    const privArea = document.getElementById('private-messages');

    document.getElementById('tab-public').classList.toggle('active', type === 'public');
    document.getElementById('tab-private').classList.toggle('active', type === 'private');

    if (type === 'public') {
        pubArea.classList.remove('hidden');
        privArea.classList.add('hidden');
        pubArea.scrollTop = pubArea.scrollHeight; // Прокрутка при поверненні
    } else {
        pubArea.classList.add('hidden');
        privArea.classList.remove('hidden');
        scrollToBottom('private-messages'); // Прокручуємо приватний при появі

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

function sendMessage(event) {
    event.preventDefault();
    const messageInput = document.getElementById('messageInput');
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
    const messageElement = document.createElement('div');
    messageElement.id = 'msg-' + message.id;

    const isMe = message.senderId === currentUser;
    messageElement.className = isMe ? 'message-box my-message' : 'message-box other-message';

    // === Логіка Аватарки та Імені ===
    const senderName = message.senderName || 'Користувач';
    const displayName = isMe ? 'Ви' : senderName;
    const firstLetter = senderName.charAt(0).toUpperCase();

    let quoteHtml = '';
    if (message.parentId) {
        const parentElem = document.getElementById('msg-' + message.parentId);
        let textToQuote = "Повідомлення не знайдено";
        let authorToQuote = "Хтось";

        if (parentElem) {
            textToQuote = parentElem.querySelector('.message-text').innerText;
            authorToQuote = parentElem.querySelector('.message-meta strong').innerText;
        }

        const shortText = textToQuote.length > 50 ? textToQuote.substring(0, 50) + '...' : textToQuote;
        quoteHtml = `
            <div class="quote-box">
                 <small class="quote-author">Відповідь для <b>${authorToQuote}</b>:</small>
                 <div class="quote-text">↳ <i>${shortText}</i></div>
            </div>`;
    }

    // Формуємо HTML повідомлення з Аватаром та правильними кнопками
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

//'use strict';
//
//let stompClient = null;
//let currentUser = null;
//let currentParentId = null;
//let currentRecipientId = 'PUBLIC'; // за замовчуванням
//let activeTab = 'public'; // Слідкуємо, де зараз користувач
//let lastDisplayedDate = null; // Змінна для відстеження дат
//
//function checkAndDisplayDate(message, targetArea) {
//    const messageDate = new Date(message.timestamp).toLocaleDateString('uk-UA', {
//        day: 'numeric',
//        month: 'long',
//        year: 'numeric'
//    });
//
//    if (lastDisplayedDate !== messageDate) {
//        const dateSeparator = document.createElement('div');
//        dateSeparator.className = 'date-separator';
//        dateSeparator.innerHTML = `<span>${messageDate}</span>`;
//        targetArea.appendChild(dateSeparator);
//        lastDisplayedDate = messageDate;
//    }
//}
//
//// В методах loadPublicHistory та loadPrivateHistory
//// ПЕРЕД початком циклу скидай: lastDisplayedDate = null;
//// ВСЕРЕДИНІ циклу перед showMessage викликай: checkAndDisplayDate(msg, chatArea);
//
//function connect() {
//    currentUser = document.querySelector('meta[name="current-user"]').content;
//    let socket = new SockJS('/ws-chat');
//    stompClient = Stomp.over(socket);
//
//    stompClient.connect({}, function (frame) {
//        stompClient.subscribe('/topic/public', function (message) {
//            handleIncomingMessage(JSON.parse(message.body), 'public');
//        });
//
//        stompClient.subscribe('/user/queue/messages', function (message) {
//            handleIncomingMessage(JSON.parse(message.body), 'private');
//        });
//
//        loadPublicHistory();
//        loadPrivateHistory(); // ТЕПЕР ВІН ВИКЛИКАЄТЬСЯ
//    });
//}
//
//// Логіка перемикання вкладок
//function switchChat(type) {
//    activeTab = type;
//    const pubArea = document.getElementById('chat-messages');
//    const privArea = document.getElementById('private-messages');
//
//    document.getElementById('tab-public').classList.toggle('active', type === 'public');
//    document.getElementById('tab-private').classList.toggle('active', type === 'private');
//
//    if (type === 'public') {
//        pubArea.classList.remove('hidden');
//        privArea.classList.add('hidden');
//    } else {
//        pubArea.classList.add('hidden');
//        privArea.classList.remove('hidden');
//        document.getElementById('private-badge').classList.add('hidden');
//        document.getElementById('private-badge').innerText = '0';
//    }
//}
//
//// Функція обробки вхідних повідомлень (вирішує куди класти і чи показувати бадж)
//function handleIncomingMessage(message, type) {
//    const targetAreaId = type === 'public' ? 'chat-messages' : 'private-messages';
//    showMessage(message, targetAreaId);
//
//    // Якщо прийшов приват, а ми на вкладці "Спільнота" — показуємо лічильник
//    if (type === 'private' && activeTab === 'public') {
//        const badge = document.getElementById('private-badge');
//        badge.classList.remove('hidden');
//        badge.innerText = parseInt(badge.innerText) + 1;
//    }
//}
//
//function loadPublicHistory() {
//    fetch('/api/chat/history/public')
//        .then(response => response.json())
//        .then(messages => {
//            const chatArea = document.getElementById('chat-messages');
//            chatArea.innerHTML = '';
//            lastDisplayedDate = null; // Скидаємо дату перед початком історії
//            messages.forEach(msg => {
//                checkAndDisplayDate(msg, chatArea);
//                showMessage(msg, 'chat-messages');
//            });
//        });
//}
//
//function loadPrivateHistory() {
//    fetch('/api/chat/history/private')
//        .then(response => response.json())
//        .then(messages => {
//            const privArea = document.getElementById('private-messages');
//            privArea.innerHTML = '';
//            lastDisplayedDate = null; // Скидаємо дату
//            messages.forEach(msg => {
//                checkAndDisplayDate(msg, privArea);
//                showMessage(msg, 'private-messages');
//            });
//        });
//}
//
//function prepareReply(messageId, senderName, type, senderUuid) {
//    currentParentId = messageId;
//    currentRecipientId = (type === 'private') ? senderUuid : 'PUBLIC';
//    // Якщо обрано приват — автоматично перемикаємо юзера на приватну вкладку
//    if (type === 'private') {
//        switchChat('private');
//    }
//
//    const replyPreview = document.getElementById('reply-preview');
//    const replyToText = document.getElementById('reply-to-text');
//
//    if (replyPreview && replyToText) {
//        const modeText = type === 'private' ? '🔒 Приватна відповідь для ' : '💬 Публічна відповідь для ';
//        replyToText.innerText = modeText + senderName;
//
//        // Можна змінити колір плашки для привату, щоб юзер бачив різницю
//        replyPreview.style.backgroundColor = type === 'private' ? '#e3f2fd' : '#f1f3f4';
//
//        replyPreview.classList.remove('hidden');
//    }
//    document.getElementById('messageInput').focus();
//}
//
//function cancelReply() {
//    currentParentId = null;
//    const replyPreview = document.getElementById('reply-preview');
//    if (replyPreview) replyPreview.classList.add('hidden');
//}
//
//// В sendMessage додаємо жорсткий фільтр
//function sendMessage(event) {
//    event.preventDefault();
//    const messageInput = document.getElementById('messageInput');
//    const messageContent = messageInput.value.trim();
//
//    if (messageContent && stompClient) {
//        let finalRecipient = currentRecipientId;
//        let destination = "/app/chat";
//
//        if (activeTab === 'private') {
//            // Якщо в приваті і отримувач не вибраний (або залишився PUBLIC від минулого разу)
//            if (!finalRecipient || finalRecipient === 'PUBLIC') {
//                finalRecipient = currentUser; // Нотатка самому собі
//            }
//            destination = "/app/chat";
//        } else {
//            // У публічній вкладці завжди PUBLIC
//            destination = "/app/chat.public";
//            finalRecipient = 'PUBLIC';
//        }
//
//        const chatMessage = {
//            senderId: currentUser,
//            recipientId: finalRecipient,
//            content: messageContent,
//            parentId: currentParentId,
//            status: 'SENT'
//        };
//
//        stompClient.send(destination, {}, JSON.stringify(chatMessage));
//
//        // --- ПІСЛЯ ВІДПРАВКИ ---
//        messageInput.value = '';
//        currentRecipientId = 'PUBLIC'; // Скидаємо отримувача в дефолт
//        currentParentId = null;        // Скидаємо батьківське повідомлення
//        cancelReply();                 // Прибираємо візуальну плашку цитування
//    }
//}
//
//function showMessage(message, targetId) {
//    const chatArea = document.getElementById(targetId);
//    const messageElement = document.createElement('div');
//    messageElement.id = 'msg-' + message.id; // Для цитування
//
//    const isMe = message.senderId === currentUser;
//    messageElement.className = isMe ? 'message-box my-message' : 'message-box other-message';
//
//    // РОЗУМНЕ ЦИТУВАННЯ
//    let quoteHtml = '';
//    if (message.parentId) {
//        const parentElem = document.getElementById('msg-' + message.parentId);
//        let textToQuote = "Повідомлення не знайдено";
//
//        if (parentElem) {
//            textToQuote = parentElem.querySelector('.message-text').innerText;
//        }
//
//        const shortText = textToQuote.length > 50 ? textToQuote.substring(0, 50) + '...' : textToQuote;
//        quoteHtml = `<div class="quote-box">↳ <i>${shortText}</i></div>`;
//    }
//
//    messageElement.innerHTML = `
//        ${quoteHtml}
//        <div class="message-meta">
//            <strong>${isMe ? 'Ви' : (message.senderName || message.senderId)}</strong>
//        </div>
//        <div class="message-text">${message.content}</div>
//
//        <div class="message-actions">
//            <button type="button" class="reply-btn" onclick="prepareReply('${message.id}', '${message.senderName || message.senderId}', 'public')">
//                Відповісти публічно
//            </button>
//
//            ${!isMe ? `
//            <button type="button" class="reply-btn private-btn"
//                onclick="prepareReply('${message.id}', '${message.senderName || message.senderId}', 'private', '${message.senderId}')">
//                Написати приватно
//            </button>
//            ` : ''}
//        </div>
//    `;
//
//    chatArea.appendChild(messageElement);
//    // Замість chatArea.scrollTop = chatArea.scrollHeight;
//    chatArea.scrollTo({
//        top: chatArea.scrollHeight,
//        behavior: 'smooth'
//    });
//}
//
//document.addEventListener('DOMContentLoaded', connect);
//document.getElementById('messageForm').addEventListener('submit', sendMessage);
//// Відправка по натисканню Enter (без Shift)
//document.getElementById('messageInput').addEventListener('keydown', function(e) {
//    if (e.key === 'Enter' && !e.shiftKey) {
//        e.preventDefault(); // Забороняємо перенос рядка
//        document.getElementById('messageForm').dispatchEvent(new Event('submit'));
//    }
//});