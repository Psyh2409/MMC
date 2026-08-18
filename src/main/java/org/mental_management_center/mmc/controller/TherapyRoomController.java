package org.mental_management_center.mmc.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.mental_management_center.mmc.model.*;
import org.mental_management_center.mmc.repository.SosRequestRepository;
import org.mental_management_center.mmc.repository.TherapyAssignmentRepository;
import org.mental_management_center.mmc.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

@Controller
@RequestMapping("/therapy")
public class TherapyRoomController {

    private final UserService userService;
    private final TherapyNoteService therapyNoteService;
    private final TherapyRoomService therapyRoomService;
    private final SharedWallService sharedWallService;
    private final TherapyAssignmentService therapyAssignmentService;
    private final NotificationService notificationService;
    private final TherapyAssignmentRepository therapyAssignmentRepository;
    private final SosRequestRepository sosRequestRepository;

    @Value("${app.jitsi.app-id}")
    private String appId;
    @Value("${app.jitsi.api-key-id}")
    private String apiKeyId;
    @Value("${app.jitsi.private-key-path}")
    private String privateKeyPath;

    public TherapyRoomController(UserService userService,
                                 TherapyNoteService therapyNoteService,
                                 TherapyRoomService therapyRoomService,
                                 SharedWallService sharedWallService,
                                 TherapyAssignmentService therapyAssignmentService,
                                 NotificationService notificationService,
                                 TherapyAssignmentRepository therapyAssignmentRepository,
                                 SosRequestRepository sosRequestRepository) {
        this.userService = userService;
        this.therapyNoteService = therapyNoteService;
        this.therapyRoomService = therapyRoomService;
        this.sharedWallService = sharedWallService;
        this.therapyAssignmentService = therapyAssignmentService;
        this.notificationService = notificationService;
        this.therapyAssignmentRepository = therapyAssignmentRepository;
        this.sosRequestRepository = sosRequestRepository;
    }

    @GetMapping("/room/{assignmentId}")
    public String getTherapyRoom(@PathVariable UUID assignmentId, Principal principal, Model model) {
        if (principal == null) return "redirect:/login";

        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        // 1. Отримуємо договір (зв'язок) замість просто профілю клієнта
        TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AccessDeniedException("Терапевтичний кабінет не знайдено."));

        User roomClient = assignment.getClient();
        User roomTherapist = assignment.getTherapist();

        // 2. ПЕРЕВІРКА РОЛЕЙ: Доступ мають ТІЛЬКИ учасники цього конкретного договору
        boolean isClient = currentUser.getId().equals(roomClient.getId());
        boolean isTherapist = currentUser.getId().equals(roomTherapist.getId());

        // Перевіряємо, чи є для цього кабінету активний екстрений виклик Адміністратора
        boolean isAdminSosAccess = currentUser.isAdmin() && sosRequestRepository.existsByAssignmentIdAndStatus(assignmentId, "PENDING");

        if (!isClient && !isTherapist && !isAdminSosAccess) {
            throw new AccessDeniedException("Конфіденційно: Ви не маєте доступу до цього кабінету.");
        }

        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new AccessDeniedException("Ця терапевтична сесія не активна.");
        }

        // Перевіряємо локальні ролі для КОНКРЕТНОЇ кімнати
        boolean isTherapistInThisRoom = currentUser.getId().equals(roomTherapist.getId());
        boolean isClientInThisRoom = currentUser.getId().equals(roomClient.getId());

        model.addAttribute("isTherapistInThisRoom", isTherapistInThisRoom);
        model.addAttribute("isClientInThisRoom", isClientInThisRoom);
        model.addAttribute("isAdminSosAccess", isAdminSosAccess);
        // 3. ІЗОЛЯЦІЯ РЕСУРСІВ: Унікальна назва прив'язана до ID договору
        String roomName = "therapy-room-" + assignment.getId();

        // Передаємо: Клієнт, Терапевт, Автор (той, хто зараз онлайн)
        String lastNote = therapyNoteService.getLastNoteContent(roomClient.getId(), roomTherapist.getId(), currentUser.getId());
        String jitsiJwt = generateJitsiJwt(currentUser, roomName);

        model.addAttribute("client", roomClient);
        model.addAttribute("therapist", roomTherapist);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("lastNoteContent", lastNote);
        model.addAttribute("roomName", roomName);
        model.addAttribute("isAdmin", currentUser.isAdmin());
        model.addAttribute("isTherapist", isTherapistInThisRoom);
        model.addAttribute("jitsiJwt", jitsiJwt);
        // Стан кімнати також відстежуємо за assignmentId
        model.addAttribute("isSessionActive", therapyRoomService.isRoomActive(assignment.getId()));

        // ====================================================================
        // ЗАВАНТАЖЕННЯ ПЕРШОЇ СТОРІНКИ СТІНИ (Абсолютно ізольовано)
        // ====================================================================
        var messagesPage = sharedWallService.getWallMessages(assignment.getId(), PageRequest.of(0, 5));

        model.addAttribute("posts", messagesPage.getContent());
        model.addAttribute("currentPage", 0);
        model.addAttribute("totalPages", messagesPage.getTotalPages());
        model.addAttribute("pageSize", 5);
        model.addAttribute("hasMore", messagesPage.hasNext());
        model.addAttribute("isWall", true);
        model.addAttribute("roomId", assignment.getId());

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



    // =========================================================
    // ОНОВЛЕНІ REST-МЕТОДИ ДЛЯ РОБОТИ ЧЕРЕЗ ASSIGNMENT_ID
    // =========================================================

    @PostMapping("/notes/save/{assignmentId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveNote(
            @PathVariable UUID assignmentId,
            @RequestParam(required = false) UUID noteId,
            @RequestBody String content,
            Principal principal) {
        try {
            User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
            TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId).orElseThrow();

            if (!hasAccessToRoom(currentUser, assignmentId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

            if (noteId == null) {
                TherapyNote newNote = therapyNoteService.saveNewNote(assignment.getTherapist(), assignment.getClient(), currentUser, content);
                return ResponseEntity.ok(Map.of("noteId", newNote.getId().toString()));
            } else {
                therapyNoteService.updateNote(noteId, content);
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/notes/get-recent/{assignmentId}")
    @ResponseBody
    public String getRecentNote(@PathVariable UUID assignmentId, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId).orElseThrow();

        if (!hasAccessToRoom(currentUser, assignmentId)) throw new AccessDeniedException("Доступ заборонено");

        return therapyNoteService.getLastNoteContent(assignment.getClient().getId(), assignment.getTherapist().getId(), currentUser.getId());
    }

    @GetMapping("/notes/history/{assignmentId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable UUID assignmentId, Principal principal) {
        try {
            User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
            TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId).orElseThrow();

            if (!hasAccessToRoom(currentUser, assignmentId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

            List<TherapyNote> notes = therapyNoteService.getHistoryForClient(assignment.getClient().getId(), currentUser.getId());
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

    @PostMapping("/room/{assignmentId}/leave")
    @ResponseBody
    public ResponseEntity<Void> leaveTherapyRoom(@PathVariable UUID assignmentId, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        if (!hasAccessToRoom(currentUser, assignmentId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (currentUser.isTherapist()) {
            therapyRoomService.deactivateRoom(assignmentId);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/room/{assignmentId}/activate")
    @ResponseBody
    public ResponseEntity<Void> activateTherapyRoom(@PathVariable UUID assignmentId, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();

        if (!hasAccessToRoom(currentUser, assignmentId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (currentUser.isTherapist()) {
            therapyRoomService.activateRoom(assignmentId);

            TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId).orElseThrow();
            notificationService.createNotification(
                    assignment.getClient(),
                    "🩺 Запрошення на терапевтичну сесію",
                    currentUser.getName() + " очікує на вас у терапевтичному кабінеті.",
                    "/therapy/room/" + assignmentId,
                    Notification.NotificationType.THERAPY_CALL
            );
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/room/{assignmentId}/status")
    @ResponseBody
    public ResponseEntity<Boolean> getRoomStatus(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(therapyRoomService.isRoomActive(assignmentId));
    }

    @PostMapping("/room/{assignmentId}/sos")
    public String triggerSos(@PathVariable UUID assignmentId,
                             @RequestParam("reason") String reason,
                             Principal principal) {
        User currentUser = userService.findByEmail(principal.getName()).orElseThrow();
        TherapyAssignment assignment = therapyAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AccessDeniedException("Кабінет не знайдено."));

        if (!currentUser.getId().equals(assignment.getClient().getId())) {
            throw new AccessDeniedException("Тільки клієнт може викликати адміністратора.");
        }

        // Записуємо окремий SOS-запит для ЦЬОГО конкретного договору
        SosRequest sosRequest = new SosRequest();
        sosRequest.setAssignment(assignment);
        sosRequest.setClient(currentUser);
        sosRequest.setReason(reason.trim());
        sosRequest.setStatus("PENDING");

        sosRequestRepository.save(sosRequest);

        return "redirect:/therapy/room/" + assignmentId + "?sos=activated";
    }

    // =========================================================
    // ЕТИЧНИЙ КОНТРОЛЬ ДОСТУПУ (1:N АРХІТЕКТУРА)
    // =========================================================
    private boolean hasAccessToRoom(User user, UUID assignmentId) {
        // Адмін має доступ, ТІЛЬКИ якщо для цієї кімнати є активний SOS
        if (user.isAdmin()) {
            if (sosRequestRepository.existsByAssignmentIdAndStatus(assignmentId, "PENDING")) {
                return true;
            }
        }

        return therapyAssignmentRepository.findById(assignmentId)
                .map(a -> "ACTIVE".equals(a.getStatus()) &&
                        (a.getClient().getId().equals(user.getId()) || a.getTherapist().getId().equals(user.getId())))
                .orElse(false);
    }
}
