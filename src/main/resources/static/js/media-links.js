window.applyMediaFacades = function() {
    // Всі класи контейнерів
    const textContainers = document.querySelectorAll('.comment-text, article.content-section, .journal-post-text, .journal-post-content');

    const ytRegex = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/(?:[^\/\n\s]+\/\S+\/|(?:v|e(?:mbed)?)\/|\S*?[?&]v=)|youtu\.be\/)([a-zA-Z0-9_-]{11})/i;
    const urlRegex = /(https?:\/\/[^\s<]+)/g;

    function walkDOM(node) {
        // Пропускаємо елементи, які ми вже створили (щоб не зациклитись)
        if (node.classList && (node.classList.contains('youtube-facade') || node.classList.contains('image-facade-wrapper'))) return;

        // СЦЕНАРІЙ 1: Для СТАТЕЙ (де посилання вже загорнуті в тег <a> редактором)
        if (node.tagName === 'A') {
            const href = node.getAttribute('href');
            if (!href || !href.startsWith('http')) return;

            // Якщо це Ютуб - замінюємо тег <a> на плеєр
            const ytMatch = href.match(ytRegex);
            if (ytMatch) {
                const videoId = ytMatch[1];
                const facade = document.createElement('div');
                facade.className = 'youtube-facade';
                facade.setAttribute('data-video-id', videoId);
                facade.innerHTML = `
                    <img src="https://img.youtube.com/vi/${videoId}/maxresdefault.jpg" alt="YouTube Video">
                    <button class="play-btn" aria-label="Play">▶</button>
                `;
                node.parentNode.replaceChild(facade, node);
                return;
            }

            // Якщо не Ютуб - намагаємось зробити прев'ю малюнка (якщо малюнок битий - повернеться оригінальний лінк)
            if (!href.includes('img.youtube.com')) {
                const originalText = node.innerHTML;
                const span = document.createElement('span');
                span.className = 'image-facade-wrapper';
                span.innerHTML = `
                    <img src="${href}" alt="Preview" class="external-image-preview" style="max-width: 100%; border-radius: 8px; display: block; margin: 10px 0;"
                         onerror="this.style.display='none'; this.nextElementSibling.style.display='inline';">
                    <a href="${href}" target="_blank" class="external-link" style="display:none; word-break: break-all;">${originalText}</a>
                `;
                node.parentNode.replaceChild(span, node);
            }
            return; // З тегом <a> закінчили
        }

        // СЦЕНАРІЙ 2: Для КОМЕНТАРІВ (де посилання - це просто "голий" текст)
        if (node.nodeType === 3) {
            const text = node.nodeValue;
            if (!urlRegex.test(text)) return; // Якщо в тексті немає лінків - пропускаємо

            const parent = node.parentNode;
            if (parent && parent.tagName === 'A') return; // Захист: якщо текст вже всередині <a>, не чіпаємо

            const tempDiv = document.createElement('span');
            tempDiv.innerHTML = text.replace(urlRegex, (match) => {
                const ytMatch = match.match(ytRegex);
                if (ytMatch) {
                    const videoId = ytMatch[1];
                    return `<div class="youtube-facade" data-video-id="${videoId}">
                                <img src="https://img.youtube.com/vi/${videoId}/maxresdefault.jpg" alt="YouTube Video">
                                <button class="play-btn" aria-label="Play">▶</button>
                            </div>`;
                }
                return `<span class="image-facade-wrapper">
                            <img src="${match}" alt="Preview" class="external-image-preview" style="max-width: 100%; border-radius: 8px; display: block; margin: 10px 0;"
                                 onerror="this.style.display='none'; this.nextElementSibling.style.display='inline';">
                            <a href="${match}" target="_blank" class="external-link" style="display:none; word-break: break-all;">${match}</a>
                        </span>`;
            });

            // Замінюємо старий голий текст на наші нові елементи
            while (tempDiv.firstChild) {
                parent.insertBefore(tempDiv.firstChild, node);
            }
            parent.removeChild(node);
            return;
        }

        // Йдемо вглиб HTML-дерева (перевіряємо кожен параграф, дів і т.д.)
        if (node.nodeType === 1) {
            Array.from(node.childNodes).forEach(child => walkDOM(child));
        }
    }

    textContainers.forEach(container => {
        if (container.dataset.linkified === 'true') return;
        // Запускаємо наш обережний прохід по всім елементам всередині контенту
        Array.from(container.childNodes).forEach(child => walkDOM(child));
        container.dataset.linkified = 'true';
    });
};

document.addEventListener('DOMContentLoaded', () => {
    window.applyMediaFacades();

    // Делегування кліку для відтворення відео Ютуба
    document.body.addEventListener('click', (e) => {
        const facade = e.target.closest('.youtube-facade');
        if (!facade) return;

        const videoId = facade.getAttribute('data-video-id');
        const iframe = document.createElement('iframe');
        iframe.setAttribute('src', `https://www.youtube.com/embed/${videoId}?autoplay=1`);
        iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
        iframe.setAttribute('allowfullscreen', 'true');
        iframe.style.width = '100%';
        iframe.style.height = '100%';
        iframe.style.position = 'absolute';
        iframe.style.top = '0';
        iframe.style.left = '0';
        iframe.style.border = 'none';
        iframe.style.borderRadius = 'var(--radius-md)';

        facade.innerHTML = '';
        facade.appendChild(iframe);
    });
});