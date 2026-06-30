// Логіка розгортання повідомлень у таблицях звернень (Адмін та Терапевт)
function toggleReply(element) {
    // 1. Отримуємо ID цільового рядка відповіді
    const targetId = element.getAttribute('data-target');
    const replyRow = document.getElementById(targetId);

    if (replyRow) {
        replyRow.classList.toggle('is-hidden');
    }

    // 2. Шукаємо елементи тексту в межах поточного рядка, на який клікнули
    const shortMsg = element.querySelector('.msg-short');
    const fullMsg = element.querySelector('.msg-full');

    // Перемикаємо видимість тільки якщо довга версія згенерована сервером
    if (shortMsg && fullMsg) {
        shortMsg.classList.toggle('is-hidden');
        fullMsg.classList.toggle('is-hidden');
    }
}