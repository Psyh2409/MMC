window.applyMediaFacades = function() {
    const textContainers = document.querySelectorAll('.comment-text, article.content-section, .journal-post-text, .journal-post-content'); //, #journalFeed, .journal-post-text, .journal-post-content, .journal-post-card, .journal-post-card.journal-post-text, .profile-main-content, .journal-section'
    console.log(`[MediaLinks] 🟢 ЗАПУСК. Знайдено контейнерів для перевірки: ${textContainers.length}`);

    // Тільки 11 символів ID! Ніякого сміття.
    const ytRegex = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/(?:watch\?v=|embed\/|live\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/i;
    const urlRegex = /(https?:\/\/[^\s<]+)/g;

    textContainers.forEach((container, index) => {
        if (container.dataset.linkified === 'true') return;
        console.log(`[MediaLinks] 👉 Аналізуємо контейнер №${index} (Клас: ${container.className})`);

        // ==========================================
        // КРОК 1: ДЛЯ СТАТЕЙ (Шукаємо готові теги <a>)
        // ==========================================
        const links = Array.from(container.querySelectorAll('a'));
        if (links.length > 0) {
            console.log(`[MediaLinks]   Знайдено тегів <a>: ${links.length}`);
        }

        links.forEach(link => {
            const href = link.getAttribute('href');
            if (!href) return;

            const ytMatch = href.match(ytRegex);
            if (ytMatch) {
                const videoId = ytMatch[1];
                console.log(`[MediaLinks]   🎥 ЮТУБ В СТАТТІ! ID: ${videoId}. Замінюємо тег <a> на плеєр.`);

                const facade = document.createElement('div');
                facade.className = 'youtube-facade';
                facade.setAttribute('data-video-id', videoId);
                facade.innerHTML = `
                    <img src="https://img.youtube.com/vi/${videoId}/maxresdefault.jpg" alt="YouTube Video">
                    <button class="play-btn" aria-label="Play">▶</button>
                `;
                // Безпечна заміна без розриву HTML!
                link.parentNode.replaceChild(facade, link);
            } else if (!href.includes('img.youtube.com')) {
                console.log(`[MediaLinks]   🖼 ІНШЕ ПОСИЛАННЯ В СТАТТІ: ${href}. Робимо спробу малюнка.`);
                // Робимо малюнок. Якщо заблокує (CORB) - чистий JS перетворить його на лінк без style=""
                const img = document.createElement('img');
                img.src = href;
                img.alt = "Медіа";
                img.className = "external-image-preview";
                img.setAttribute("onerror", "console.warn('[MediaLinks] ⚠️ Гугл заблокував малюнок! Робимо звичайний лінк:', this.src); this.replaceWith(Object.assign(document.createElement('a'), {href: this.src, textContent: this.src, className: 'external-link', target: '_blank'}));");

                link.parentNode.replaceChild(img, link);
            }
        });

        // ==========================================
        // КРОК 2: ДЛЯ КОМЕНТАРІВ (Шукаємо "голий" текст)
        // ==========================================
        const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
        const textNodes = [];
        let node;
        while (node = walker.nextNode()) {
            // Пропускаємо тексти, які вже є посиланнями або всередині нашого відео
            if (node.parentNode.tagName !== 'A' && !node.parentNode.closest('.youtube-facade') && !node.parentNode.closest('.external-image-preview')) {
                textNodes.push(node);
            }
        }

        textNodes.forEach(textNode => {
            const text = textNode.nodeValue;
            if (!urlRegex.test(text)) return;

            console.log(`[MediaLinks]   📝 Знайдено текст із посиланням: ${text.trim().substring(0, 30)}...`);

            const tempDiv = document.createElement('span');
            tempDiv.innerHTML = text.replace(urlRegex, (match) => {
                const ytMatch = match.match(ytRegex);
                if (ytMatch) {
                    const videoId = ytMatch[1];
                    console.log(`[MediaLinks]   🎥 ЮТУБ В ТЕКСТІ! ID: ${videoId}`);
                    return `<div class="youtube-facade" data-video-id="${videoId}">
                                <img src="https://img.youtube.com/vi/${videoId}/maxresdefault.jpg" alt="YouTube Video">
                                <button class="play-btn" aria-label="Play">▶</button>
                            </div>`;
                }

                console.log(`[MediaLinks]   🖼 СПРОБА МАЛЮНКА В ТЕКСТІ: ${match}`);
                return `<img src="${match}" alt="Медіа" class="external-image-preview" onerror="console.warn('[MediaLinks] ⚠️ Малюнок не завантажився (Гугл/CORB)! Перетворюю на лінк:', this.src); this.replaceWith(Object.assign(document.createElement('a'), {href: this.src, textContent: this.src, className: 'external-link', target: '_blank'}));">`;
            });

            while (tempDiv.firstChild) {
                textNode.parentNode.insertBefore(tempDiv.firstChild, textNode);
            }
            textNode.parentNode.removeChild(textNode);
        });

        container.dataset.linkified = 'true';
    });
    console.log(`[MediaLinks] 🛑 ОБРОБКУ ЗАВЕРШЕНО.`);
};

document.addEventListener('DOMContentLoaded', () => {
    window.applyMediaFacades();

    // Запуск відео при кліку
    document.body.addEventListener('click', (e) => {
        const facade = e.target.closest('.youtube-facade');
        if (!facade) return;

        console.log(`[MediaLinks] ▶️ Клік по відео! Запускаємо iframe.`);
        const videoId = facade.getAttribute('data-video-id');
        const iframe = document.createElement('iframe');
        iframe.setAttribute('src', `https://www.youtube.com/embed/${videoId}?autoplay=1`);
        iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
        iframe.setAttribute('allowfullscreen', 'true');
        iframe.className = 'youtube-iframe'; // Жодних інлайнових стилів!

        facade.innerHTML = '';
        facade.appendChild(iframe);
    });
});
