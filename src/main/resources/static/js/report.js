// Відкриття модального вікна скарги
function openReportModal(targetId, targetType) {
    const modal = document.getElementById('reportModal');
    if (!modal) return;

    document.getElementById('reportTargetId').value = targetId;
    document.getElementById('reportTargetType').value = targetType;
    document.getElementById('reportCustomReason').value = '';
    modal.classList.remove('is-hidden');
}

// Закриття модального вікна
function closeReportModal() {
    const modal = document.getElementById('reportModal');
    if (modal) {
        modal.classList.add('is-hidden');
    }
}

// Відправка скарги через AJAX
function submitReport(event) {
    event.preventDefault();

    const targetId = document.getElementById('reportTargetId').value;
    const targetType = document.getElementById('reportTargetType').value;

    const selectedPreset = document.querySelector('input[name="reasonPreset"]:checked').value;
    const customReason = document.getElementById('reportCustomReason').value.trim();

    let finalReason = selectedPreset;
    if (selectedPreset === 'CUSTOM' || customReason.length > 0) {
        finalReason = customReason.length > 0
            ? (selectedPreset !== 'CUSTOM' ? selectedPreset + ": " + customReason : customReason)
            : selectedPreset;
    }

    const csrfInput = document.querySelector('input[name="_csrf"]');
    const csrfToken = csrfInput ? csrfInput.value : '';

    fetch('/api/reports', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': csrfToken
        },
        body: JSON.stringify({
            targetId: targetId,
            targetType: targetType,
            reason: finalReason
        })
    })
    .then(response => {
        if (response.ok) {
            alert('Дякуємо! Вашу скаргу відправлено на розгляд адміністратору.');
            closeReportModal();
        } else if (response.status === 409) {
            alert('Ви вже відправляли скаргу на цей матеріал.');
            closeReportModal();
        } else {
            alert('Не вдалося відправити скаргу. Спробуйте пізніше.');
        }
    })
    .catch(error => console.error('Помилка відправки скарги:', error));
}

// Делегування подій для кнопок «Поскаржитися»
document.addEventListener('click', function(event) {
    const reportBtn = event.target.closest('.btn-report-action');
    if (reportBtn) {
        const targetId = reportBtn.getAttribute('data-id');
        const targetType = reportBtn.getAttribute('data-type');
        if (targetId && targetType) {
            openReportModal(targetId, targetType);
        }
    }
});

function openDeleteCommentModal(commentId) {
    const modal = document.getElementById('deleteCommentModal');
    const form = document.getElementById('deleteCommentForm');
    if (modal && form) {
        form.action = '/articles/comments/' + commentId + '/delete';
        document.getElementById('deletionReasonInput').value = '';
        modal.classList.remove('is-hidden');
    }
}

function closeDeleteCommentModal() {
    const modal = document.getElementById('deleteCommentModal');
    if (modal) {
        modal.classList.add('is-hidden');
    }
}

// Делегування події для відкриття модального вікна видалення коментаря
document.addEventListener('click', function(event) {
    const deleteBtn = event.target.closest('.btn-delete-comment-action');
    if (deleteBtn) {
        const commentId = deleteBtn.getAttribute('data-id');
        if (commentId && typeof window.openDeleteCommentModal === 'function') {
            window.openDeleteCommentModal(commentId);
        }
    }
});