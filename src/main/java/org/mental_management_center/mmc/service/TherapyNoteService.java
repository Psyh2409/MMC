package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TherapyNoteService {

    private final TherapyNoteRepository repository;

    public TherapyNoteService(TherapyNoteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TherapyNote saveNewNote(User therapist, User client, User author, String content) {
        TherapyNote note = new TherapyNote();
        note.setTherapist(therapist);
        note.setClient(client);
        note.setAuthor(author);
        note.setContent(content);
        return repository.save(note);
    }

    @Transactional
    public void updateNote(UUID noteId, String content) {
        repository.findById(noteId).ifPresent(note -> {
            note.setContent(content);
            // Репозиторій сам оновить запис
        });
    }

    public List<TherapyNote> getHistoryForClient(UUID clientUuid, UUID authorId) {
        // Використовуємо твій наявний репозиторій, але можна додати фільтр за клієнтом
        return repository.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
                .filter(n -> n.getClient().getId().equals(clientUuid))
                .toList();
    }

    public String getLastNoteContent(UUID clientId, UUID therapistId, UUID authorId) {
        return repository.findTopByClientIdAndTherapistIdAndAuthorIdOrderByCreatedAtDesc(
                        clientId, therapistId, authorId)
                .filter(note -> note.getCreatedAt().isAfter(LocalDateTime.now().minusHours(1)))
                .map(TherapyNote::getContent)
                .orElse("");
    }

    // Для UserProfileController
    public List<TherapyNote> getNotesByAuthor(UUID authorId) {
        return repository.findByAuthorIdOrderByCreatedAtDesc(authorId);
    }
}