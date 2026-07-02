// js/article-form.js

// 1. СИНХРОНІЗАЦІЯ КАТЕГОРІЙ
function syncCategory(select) {
    const slugInput = document.getElementById('category');
    const nameInput = document.getElementById('categoryNameUa');

    if (select.value === 'NEW') {
        slugInput.classList.remove('hidden-category-field');
        nameInput.classList.remove('hidden-category-field');
        slugInput.value = '';
        nameInput.value = '';
    } else if (select.value !== "") {
        slugInput.classList.add('hidden-category-field');
        nameInput.classList.add('hidden-category-field');
        slugInput.value = select.value;
        nameInput.value = select.options[select.selectedIndex].text;
        console.log("Синхронізація: Slug=" + slugInput.value + ", Name=" + nameInput.value);
    }
}

// 2. ГОЛОВНИЙ ОБРОБНИК СТОРІНКИ ПІСЛЯ ЗАВАНТАЖЕННЯ
document.addEventListener('DOMContentLoaded', function() {
    // Частина А: Авто-підтягування тематичного розділу
    const selector = document.getElementById('categorySelector');
    const slugInput = document.getElementById('category');

    if (selector && slugInput && slugInput.value) {
        selector.value = slugInput.value;
        if (selector.value !== slugInput.value && slugInput.value !== '') {
            selector.value = 'NEW';
            syncCategory(selector);
        }
    }

    // Частина Б: Асинхронне завантаження медіафайлів
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('asyncMediaFile');
    const uploadQueue = document.getElementById('mediaUploadQueue');
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;

    if (dropZone && fileInput && uploadQueue) {
        dropZone.addEventListener('click', () => fileInput.click());

        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropZone.classList.add('drag-over');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropZone.classList.remove('drag-over');
            }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            const dt = e.dataTransfer;
            if (dt && dt.files.length > 0) {
                handleFilesUpload(dt.files);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) {
                handleFilesUpload(fileInput.files);
            }
        });

        function handleFilesUpload(files) {
            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                const formData = new FormData();
                formData.append('file', file);

                const progressRow = document.createElement('div');
                progressRow.className = 'media-item-row';
                progressRow.innerHTML = `<span>Завантаження ${file.name}...</span>`;
                uploadQueue.appendChild(progressRow);

                fetch('/api/media/upload', {
                    method: 'POST',
                    headers: { 'X-CSRF-TOKEN': csrfToken },
                    body: formData
                })
                .then(response => {
                    if (!response.ok) throw new Error('Помилка сервера');
                    return response.json();
                })
                .then(data => {
                    let generatedTag = file.type.startsWith('video/')
                        ? `<video src="${data.url}" class="article-illustration" controls preload="metadata"></video>`
                        : `<img src="${data.url}" class="article-illustration" alt="${file.name}">`;

                    progressRow.innerHTML = `
                        <span class="media-item-name">${file.name}</span>
                        <input type="text" class="form-input media-item-input" value='${generatedTag}' readonly>
                        <button type="button" class="btn-primary btn-sm copy-btn">Копіювати код</button>
                    `;

                    const inputEl = progressRow.querySelector('.media-item-input');
                    const btnEl = progressRow.querySelector('.copy-btn');

                    if (inputEl) inputEl.addEventListener('click', () => inputEl.select());
                    if (btnEl) {
                        btnEl.addEventListener('click', () => {
                            navigator.clipboard.writeText(generatedTag).then(() => {
                                const originalText = btnEl.textContent;
                                btnEl.textContent = 'Скопійовано! ✓';
                                btnEl.classList.add('btn-success');
                                setTimeout(() => {
                                    btnEl.textContent = originalText;
                                    btnEl.classList.remove('btn-success');
                                }, 2000);
                            });
                        });
                    }
                })
                .catch(error => {
                    progressRow.innerHTML = `<span class="error-text">Помилка завантаження: ${error.message}</span>`;
                });
            }
            fileInput.value = '';
        }
    }
});