package org.mental_management_center.mmc.service;

import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapyNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TherapyNoteService {

    private final TherapyNoteRepository repository;

    public TherapyNoteService(TherapyNoteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveOrUpdateNote(User therapist, User client, String content) {
        // Шукаємо останню нотатку. Якщо вона створена менше ніж 12 годин тому — оновлюємо її.
        // Якщо ні — створюємо нову сесію (запис).
        repository.findTopByClientIdAndTherapistIdOrderByCreatedAtDesc(client.getId(), therapist.getId())
                .filter(note -> note.getCreatedAt().isAfter(LocalDateTime.now().minusHours(12)))
                .ifPresentOrElse(
                        existingNote -> {
                            existingNote.setContent(content);
                            repository.save(existingNote);
                        },
                        () -> {
                            TherapyNote newNote = new TherapyNote();
                            newNote.setTherapist(therapist);
                            newNote.setClient(client);
                            newNote.setContent(content);
                            repository.save(newNote);
                        }
                );
    }

    public String getLastNoteContent(UUID clientId, UUID therapistId) {
        return repository.findTopByClientIdAndTherapistIdOrderByCreatedAtDesc(clientId, therapistId)
                .map(TherapyNote::getContent)
                .orElse("");
    }
}