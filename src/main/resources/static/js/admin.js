function filterTable(role) {
    const rows = document.querySelectorAll('table tbody tr');

    rows.forEach(row => {
        const rowRole = row.getAttribute('data-role');
        if (role === 'ALL' || rowRole === role) {
            row.style.display = '';
        } else {
            row.style.display = 'none';
        }

    })
}