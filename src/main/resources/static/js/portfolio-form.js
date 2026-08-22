document.addEventListener('DOMContentLoaded', () => {
    const practiceTypeSelect = document.getElementById('practiceType');
    const ethicsBlock = document.getElementById('ethicsBlock');
    const ethicsCheckbox = document.getElementById('nonMedicalCompetenceAware');

    // Перевіряємо, чи існує селект на поточній сторінці (захист від помилок у консолі)
    if (!practiceTypeSelect || !ethicsBlock || !ethicsCheckbox) return;

    // Функція перемикання видимості та обов'язковості чекбокса
    const toggleEthicsBlock = () => {
        if (practiceTypeSelect.value === 'NON_MEDICAL') {
            ethicsBlock.classList.remove('is-hidden');
            ethicsCheckbox.required = true;
        } else {
            ethicsBlock.classList.add('is-hidden');
            ethicsCheckbox.required = false;
            ethicsCheckbox.checked = false; // Скидаємо прапорець, якщо обрали медичний підхід
        }
    };

    // Слухаємо зміни у випадаючому списку
    practiceTypeSelect.addEventListener('change', toggleEthicsBlock);

    // Викликаємо одразу при завантаженні для відновлення правильного стану (при редагуванні)
    toggleEthicsBlock();
});