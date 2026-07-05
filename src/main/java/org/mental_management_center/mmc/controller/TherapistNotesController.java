package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/therapist/notes")
@RequiredArgsConstructor
public class TherapistNotesController {

    private final TherapyNoteService therapyNoteService;
    private final UserService userService;

    // 1. ПОКАЗАТИ НОТАТКИ ДЛЯ КОНКРЕТНОГО КЛІЄНТА
    @GetMapping("/{clientId}")
    @PreAuthorize("hasRole('THERAPIST')")
    public String showClientNotes(@PathVariable UUID clientId, Model model, Principal principal) {
        User therapist = userService.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Фахівця не знайдено"));

        User client = userService.findById(clientId);
        if (client == null) {
            throw new RuntimeException("Клієнта не знайдено");
        }

        // Використовуємо твій існуючий метод сервісу!
        List<TherapyNote> notes = therapyNoteService.getHistoryForClient(clientId, therapist.getId());

        model.addAttribute("client", client);
        model.addAttribute("notes", notes);

        return "therapist-client-notes"; // Новий шаблон, який ми зараз створимо
    }

    // 2. СТВОРИТИ НОВУ НОТАТКУ
    @PostMapping("/{clientId}/create")
    @PreAuthorize("hasRole('THERAPIST')")
    public String createNote(@PathVariable UUID clientId, @RequestParam("content") String content, Principal principal) {
        User therapist = userService.findByEmail(principal.getName()).orElseThrow();
        User client = userService.findById(clientId);

        // Використовуємо твій існуючий метод saveNewNote (therapist, client, author, content)
        therapyNoteService.saveNewNote(therapist, client, therapist, content);

        return "redirect:/therapist/notes/" + clientId;
    }

    // 3. ВИДАЛИТИ НОТАТКУ
    @PostMapping("/{clientId}/delete/{noteId}")
    @PreAuthorize("hasRole('THERAPIST')")
    public String deleteNote(@PathVariable UUID clientId, @PathVariable UUID noteId) {
        // Використовуємо твій існуючий метод видалення
        therapyNoteService.deleteNote(noteId);
        return "redirect:/therapist/notes/" + clientId;
    }

    // 4. РЕДАГУВАТИ НОТАТКУ
    @PostMapping("/{clientId}/edit/{noteId}")
    @PreAuthorize("hasRole('THERAPIST')")
    public String editNote(@PathVariable UUID clientId, @PathVariable UUID noteId, @RequestParam("content") String content) {
        // Використовуємо існуючий метод оновлення
        therapyNoteService.updateNote(noteId, content);
        return "redirect:/therapist/notes/" + clientId;
    }
}