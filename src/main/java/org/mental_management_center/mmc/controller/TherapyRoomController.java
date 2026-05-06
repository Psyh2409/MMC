package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequestMapping("/therapy")
public class TherapyRoomController {

    private final UserService userService;
    private final TherapyNoteService therapyNoteService;

    public TherapyRoomController(UserService userService, TherapyNoteService therapyNoteService) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
    }

    @GetMapping("/room/{clientUuid}")
    public String getTherapyRoom(@PathVariable UUID clientUuid, Principal principal, Model model) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        User roomOwner = userService.findById(clientUuid);

        if (roomOwner == null) return "error/404";

        // Тільки адмін (терапевт) або власник кімнати мають доступ
        if (!currentUser.isAdmin() && !currentUser.getId().equals(clientUuid)) return "error/403";

        // Завантажуємо існуючу нотатку для цієї сесії
        String lastNote = therapyNoteService.getLastNoteContent(clientUuid, currentUser.getId());

        model.addAttribute("client", roomOwner);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("lastNoteContent", lastNote);
        model.addAttribute("roomName", "mmc-room-" + clientUuid);

        return "therapy-room";
    }

    @PostMapping("/notes/save/{clientUuid}")
    @ResponseBody
    public ResponseEntity<String> saveNote(@PathVariable UUID clientUuid,
                                           @RequestBody String content,
                                           Principal principal) {
        User therapist = userService.findByEmail(principal.getName()).orElseThrow();
        User client = userService.findById(clientUuid);

        therapyNoteService.saveOrUpdateNote(therapist, client, content);
        return ResponseEntity.ok("Saved");
    }
}