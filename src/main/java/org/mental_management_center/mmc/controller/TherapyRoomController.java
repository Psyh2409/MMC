package org.mental_management_center.mmc.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Principal;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

@Controller
@RequestMapping("/therapy")
public class TherapyRoomController {

    private final UserService userService;
    private final TherapyNoteService therapyNoteService;

    // Дані з вашої панелі JaaS (краще винести в application.properties)
    private final String APP_ID = "vpaas-magic-cookie-a6c49e33cd42404bb9c7e3d27f7825c6";
    private final String API_KEY_ID = "vpaas-magic-cookie-a6c49e33cd42404bb9c7e3d27f7825c6/bef9f5"; // Замініть на Key ID з панелі
    private final String PRIVATE_KEY_PATH = "C:\\Users\\ASUS\\MyMMCv1\\JaaS_security\\MMC_JaaS.pk"; // Шлях до вашого файлу .pk

    public TherapyRoomController(UserService userService, TherapyNoteService therapyNoteService) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
    }

    @GetMapping("/room/{clientUuid}")
    public String getTherapyRoom(@PathVariable UUID clientUuid, Principal principal, Model model) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        // Якщо у вас тут раніше було просто = userService.findById, залиште як було у вас
        User roomOwner = userService.findById(clientUuid).orElseThrow();

        if (!currentUser.isAdmin() && !currentUser.getId().equals(clientUuid)) return "error/403";

        // ВИЗНАЧАЄМО ТЕРАПЕВТА ДЛЯ БАЗИ ДАНИХ
        User therapist;
        if (currentUser.isAdmin()) {
            therapist = currentUser;
        } else {
            therapist = userService.findAdmin();
        }

        String roomName = "therapy-room-" + clientUuid;
        // Передаємо: Клієнт, Терапевт, Автор (той, хто зараз онлайн)
        String lastNote = therapyNoteService.getLastNoteContent(roomOwner.getId(), therapist.getId(), currentUser.getId());

        String jitsiJwt = generateJitsiJwt(currentUser, roomName);

        model.addAttribute("client", roomOwner);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("lastNoteContent", lastNote);
        model.addAttribute("roomName", roomName);
        model.addAttribute("isAdmin", currentUser.isAdmin());
        model.addAttribute("jitsiJwt", jitsiJwt);

        return "therapy-room";
    }

    private String generateJitsiJwt(User user, String roomName) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println("ENTERING generateJitsiJwt()");
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        try {
            long now = System.currentTimeMillis();
            long exp = now + (3 * 60 * 60 * 1000); // Токен діє 3 години

            Map<String, Object> userContext = new HashMap<>();
            userContext.put("name", user.getName());
            userContext.put("email", user.getEmail());
            userContext.put("id", user.getId().toString());

            Map<String, Object> context = new HashMap<>();
            context.put("user", userContext);
            context.put("features", Map.of(
                    "livestreaming", true,
                    "recording", true,
                    "transcription", true
            ));

            return Jwts.builder()
                    .setHeaderParam("kid", API_KEY_ID)
                    .setHeaderParam("typ", "JWT")
                    .setIssuer("chat")
                    .setSubject(APP_ID)
                    .setAudience("jitsi")
                    .setExpiration(new Date(exp))
                    .setIssuedAt(new Date(now))
                    .claim("room", roomName)
                    .claim("context", context)
                    .signWith(loadPrivateKey(), SignatureAlgorithm.RS256)
                    .compact();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(PRIVATE_KEY_PATH));
        String privateKeyContent = new String(keyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        System.out.println(privateKeyContent.substring(0, 5) + "..." + privateKeyContent.substring(privateKeyContent.length() - 5));
        byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }



    @PostMapping("/notes/save/{clientUuid}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveNote(
            @PathVariable UUID clientUuid,
            @RequestParam(required = false) UUID noteId, // Отримуємо ID, якщо він уже є у фронтенда
            @RequestBody String content,
            Principal principal) {

        try {
            User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
            User client = userService.findById(clientUuid).orElseThrow();
            // Терапевт — або адмін, або поточний юзер
            User therapist = currentUser.isAdmin() ? currentUser : userService.findAdmin();

            if (noteId == null) {
                // Це перше збереження за сесію — створюємо новий запис
                TherapyNote newNote = therapyNoteService.saveNewNote(therapist, client, currentUser, content);
                // Повертаємо ID нової нотатки фронтенду
                return ResponseEntity.ok(Map.of("noteId", newNote.getId().toString()));
            } else {
                // У нас вже є ID, значить просто оновлюємо існуючу нотатку
                therapyNoteService.updateNote(noteId, content);
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/notes/get-recent/{clientUuid}")
    @ResponseBody
    public String getRecentNote(@PathVariable UUID clientUuid, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        User client = userService.findById(clientUuid).orElseThrow();
        User therapist = currentUser.isAdmin() ? currentUser : userService.findAdmin();

        return therapyNoteService.getLastNoteContent(client.getId(), therapist.getId(), currentUser.getId());
    }

    @GetMapping("/notes/history/{clientUuid}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable UUID clientUuid, Principal principal) {
        try {
            User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

            // Отримуємо всі нотатки, де автором є поточний користувач, а клієнтом — цей UUID
            List<TherapyNote> notes = therapyNoteService.getHistoryForClient(clientUuid, currentUser.getId());

            // Перетворюємо список об'єктів у простий формат для JSON
            List<Map<String, Object>> response = notes.stream().map(note -> {
                Map<String, Object> map = new HashMap<>();
                map.put("content", note.getContent());
                map.put("createdAt", note.getCreatedAt());
                return map;
            }).toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}