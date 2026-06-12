// Оголошуємо глобальну функцію, щоб її міг викликати journal.js після fetch
window.applyMediaFacades = function() {
    const textContainers = document.querySelectorAll('.comment-text, .article-content, .journal-post-text, .journal-post-content');

    textContainers.forEach(container => {
        if (container.dataset.linkified === 'true') return;

        let html = container.innerHTML;

        // Патерни (лапка не закрита спеціально!)
        const youtubeRegex = /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/(?:[^\/\n\s]+\/\S+\/|(?:v|e(?:mbed)?)\/|\S*?[?&]v=)|youtu\.be\/)([a-zA-Z0-9_-]{11})(?:\S+)?/gi;
        const urlRegex = /(?<!src=")(https?:\/\/[^\s]+)/g;

        // Фасад для YouTube
        html = html.replace(youtubeRegex, (match, videoId) => {
            return `
                <div class="youtube-facade" data-video-id="${videoId}">
                    <img src="https://img.youtube.com/vi/${videoId}/maxresdefault.jpg" alt="YouTube Video">
                    <button class="play-btn" aria-label="Play">▶</button>
                </div>
            `;
        });

        // Інші посилання
        html = html.replace(urlRegex, (match) => {
            if (match.includes('img.youtube.com')) return match;
            return `<a href="${match}" target="_blank" rel="noopener noreferrer" class="external-link">${match}</a>`;
        });

        container.innerHTML = html;
        container.dataset.linkified = 'true';
    });
};

// Запускаємо при першому завантаженні сторінки
document.addEventListener('DOMContentLoaded', () => {
    window.applyMediaFacades();

    // Делегування кліку для відтворення відео
    document.body.addEventListener('click', (e) => {
        const facade = e.target.closest('.youtube-facade');
        if (!facade) return;

        const videoId = facade.getAttribute('data-video-id');

        const iframe = document.createElement('iframe');
        iframe.setAttribute('src', `https://www.youtube.com/embed/${videoId}?autoplay=1`);
        iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
        iframe.setAttribute('allowfullscreen', 'true');
        iframe.classList.add('youtube-iframe');

        facade.replaceWith(iframe);
    });
});