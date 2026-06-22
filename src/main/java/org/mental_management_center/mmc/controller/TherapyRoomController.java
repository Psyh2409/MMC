package org.mental_management_center.mmc.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.RoleBit;
import org.mental_management_center.mmc.model.TherapyNote;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.SharedWallService;
import org.mental_management_center.mmc.service.TherapyRoomService;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.service.TherapyNoteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
    private final TherapyRoomService therapyRoomService;
    private final SharedWallService sharedWallService;

    @Value("${app.jitsi.app-id}")
    private String appId;
    @Value("${app.jitsi.api-key-id}")
    private String apiKeyId;
    @Value("${app.jitsi.private-key-path}")
    private String privateKeyPath;

    public TherapyRoomController(UserService userService, TherapyNoteService therapyNoteService, TherapyRoomService therapyRoomService, SharedWallService sharedWallService) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
        this.therapyRoomService = therapyRoomService;
        this.sharedWallService = sharedWallService;
    }

    @GetMapping("/room/{clientUuid}")
    public String getTherapyRoom(@PathVariable UUID clientUuid, Principal principal, Model model) {
        if (principal == null) return "redirect:/login";

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        User roomOwner = userService.findById(clientUuid).orElseThrow();

        // 1. ПЕРЕВІРКА РОЛЕЙ: Чи є поточний юзер фахівцем (Адмін для v1.0 або Терапевт для v2.0)
        boolean isAuthorizedProfessional = currentUser.isAdmin() || currentUser.isTherapist();

        // TODO v2.0: Додати перевірку, чи цей Терапевт дійсно закріплений за цим Клієнтом
        // if (currentUser.isTherapist() && !roomOwner.getTherapistId().equals(currentUser.getId())) return "error/403";

        // 2. ПЕРЕВІРКА ДОСТУПУ: В кімнату може зайти або фахівець, або сам власник кімнати (клієнт)
        if (!isAuthorizedProfessional && !currentUser.getId().equals(clientUuid)) {
            return "error/403";
        }

        // 3. === НАШ ВИМИКАЧ КНОПКИ ===
        // Якщо зайшов фахівець — запалюємо кнопку в профілі клієнта
        if (isAuthorizedProfessional) {
            therapyRoomService.activateRoom(clientUuid);
        }

        // 4. ВИЗНАЧАЄМО ТЕРАПЕВТА ДЛЯ БАЗИ ДАНИХ (для завантаження нотаток)
        User therapist;
        if (isAuthorizedProfessional) {
            therapist = currentUser; // Фахівець сам веде сесію
        } else {
            // Якщо зайшов клієнт, нам треба знати, хто його терапевт.
            // Для v1.0 це завжди Admin. У v2.0 тут буде щось на кшталт roomOwner.getAssignedTherapist()
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
        model.addAttribute("isTherapist", currentUser.isTherapist()); // Передаємо у в'юху для можливих UI-рішень
        model.addAttribute("jitsiJwt", jitsiJwt);
        model.addAttribute("isSessionActive", therapyRoomService.isRoomActive(clientUuid));

        // ====================================================================
        // 2. НОВИЙ БЛОК: ЗАВАНТАЖЕННЯ ПЕРШОЇ СТОРІНКИ СТІНИ ДЛЯ СТАРТУ
        // ====================================================================
        // Беремо перші 5 повідомлень (page 0, size 5)
        var messagesPage = sharedWallService.getWallMessages(clientUuid, PageRequest.of(0, 5));

        model.addAttribute("posts", messagesPage.getContent());
        model.addAttribute("currentPage", 0);
        model.addAttribute("totalPages", messagesPage.getTotalPages());
        model.addAttribute("pageSize", 5);
        model.addAttribute("hasMore", messagesPage.hasNext());
        model.addAttribute("isWall", true); // Маячок для фронтенду
        model.addAttribute("roomId", clientUuid);

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
                    .setHeaderParam("kid", apiKeyId)
                    .setHeaderParam("typ", "JWT")
                    .setIssuer("chat")
                    .setSubject(appId)
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
        byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
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

    @PostMapping("/room/{clientUuid}/leave")
    @ResponseBody
    public ResponseEntity<Void> leaveTherapyRoom(@PathVariable UUID clientUuid, Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        boolean isAuthorizedProfessional = currentUser.isAdmin() || currentUser.isTherapist();

        // Якщо це вийшов фахівець — гасимо світло (ховаємо кнопку)
        if (isAuthorizedProfessional) {
            therapyRoomService.deactivateRoom(clientUuid);
        }

        return ResponseEntity.ok().build();
    }

//    @PostMapping("/room/{clientUuid}/activate")
//    @ResponseBody
//    public ResponseEntity<Void> activateTherapyRoom(@PathVariable UUID clientUuid, Principal principal) {
//        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//
//        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
//        boolean isAuthorizedProfessional = currentUser.isAdmin() || currentUser.isTherapist();
//
//        if (isAuthorizedProfessional) {
//            therapyRoomService.activateRoom(clientUuid);
//        }
//
//        return ResponseEntity.ok().build();
//    }
}