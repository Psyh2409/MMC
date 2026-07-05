// Прев'ю файлу перед публікацією
function handlePublicWallMediaPreview(input) {
    const previewImg = document.getElementById('wall-preview-img');
    const previewVid = document.getElementById('wall-preview-vid');
    const fileNameDisplay = document.getElementById('wall-file-name');

    // Скидаємо попередній стан
    previewImg.classList.add('is-hidden');
    previewVid.classList.add('is-hidden');
    previewImg.src = '';
    previewVid.src = '';
    fileNameDisplay.textContent = '';

    if (input.files && input.files[0]) {
        const file = input.files[0];
        fileNameDisplay.textContent = file.name;
        const url = URL.createObjectURL(file);

        if (file.type.startsWith('video/')) {
            previewVid.src = url;
            previewVid.classList.remove('is-hidden');
        } else if (file.type.startsWith('image/')) {
            previewImg.src = url;
            previewImg.classList.remove('is-hidden');
        }
    }
}

// Перемикач режиму Редагування/Читання
function togglePublicPostEdit(postId) {
    const readBox = document.getElementById('post-read-' + postId);
    const editBox = document.getElementById('post-edit-' + postId);

    if (editBox.classList.contains('is-hidden')) {
        readBox.classList.add('is-hidden');
        editBox.classList.remove('is-hidden');
    } else {
        readBox.classList.remove('is-hidden');
        editBox.classList.add('is-hidden');
    }
}

function togglePublicPostEdit(postId) {
    const readBox = document.getElementById('post-read-' + postId);
    const editBox = document.getElementById('post-edit-' + postId);

    if (editBox && readBox) {
        readBox.classList.toggle('is-hidden');
        editBox.classList.toggle('is-hidden');
    }
}