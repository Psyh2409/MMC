'use strict';

let stompClient = null;
let currentUser = null;

function connect() {
    currentUser = document.querySelector('meta[name="current-user"]').content;
    let socket = new SockJS('/ws-chat');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('Connected to Common Space: ' + frame);

        // Підписуємося на ЗАГАЛЬНУ чергу (чуємо всіх)
        stompClient.subscribe('/topic/public', function (message) {
            showMessage(JSON.parse(message.body));
        });

        loadPublicHistory();
    });
}

function loadPublicHistory() {
    fetch('/api/chat/history/public')
        .then(response => response.json())
        .then(messages => {
            const chatArea = document.getElementById('chat-messages');
            chatArea.innerHTML = '';
            messages.forEach(msg => showMessage(msg));
        });
}

function sendMessage(event) {
    event.preventDefault();
    const messageInput = document.getElementById('messageInput');
    const messageContent = messageInput.value.trim();

    if (messageContent && stompClient) {
        const chatMessage = {
            senderId: currentUser,
            recipientId: 'PUBLIC',
            content: messageContent,
            status: 'SENT'
        };

        // Відправляємо в ПУБЛІЧНИЙ канал
        stompClient.send("/app/chat.public", {}, JSON.stringify(chatMessage));
        messageInput.value = '';
    }
}

function showMessage(message) {
    const chatArea = document.getElementById('chat-messages');
    const messageElement = document.createElement('div');

    // Стилізація
    messageElement.style.padding = '10px 15px';
    messageElement.style.borderRadius = '8px';
    messageElement.style.maxWidth = '80%';
    messageElement.style.wordWrap = 'break-word';

    if (message.senderId === currentUser) {
        // Мої повідомлення (справа)
        messageElement.style.alignSelf = 'flex-end';
        messageElement.style.backgroundColor = '#eefeeb'; // Світло-зелений фон
        messageElement.style.border = '1px solid #c8e6c9';
        messageElement.innerHTML = `<strong>Ви:</strong> ${message.content}`;
    } else {
        // Чужі повідомлення (зліва)
        messageElement.style.alignSelf = 'flex-start';
        messageElement.style.backgroundColor = '#f5f5f5'; // Сіруватий фон
        messageElement.style.border = '1px solid #e0e0e0';
        messageElement.innerHTML = `<strong style="color: #4e342e;">${message.senderId}:</strong> ${message.content}`;
    }

    chatArea.appendChild(messageElement);
    chatArea.scrollTop = chatArea.scrollHeight;
}

// Автоматично підключаємось при відкритті сторінки
document.addEventListener('DOMContentLoaded', connect);
document.getElementById('messageForm').addEventListener('submit', sendMessage);