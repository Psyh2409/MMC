package org.mental_management_center.mmc.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.service.UserService;
import org.mental_management_center.mmc.service.TherapyNoteService;
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
        User roomOwner = userService.findById(clientUuid);

        if (roomOwner == null) return "error/404";
        if (!currentUser.isAdmin() && !currentUser.getId().equals(clientUuid)) return "error/403";

        String roomName = "therapy-room-" + clientUuid;
        String lastNote = therapyNoteService.getLastNoteContent(clientUuid, currentUser.getId());

        // Генерируємо токен для Jitsi
        String jitsiJwt = generateJitsiJwt(currentUser, roomName);

        model.addAttribute("client", roomOwner);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("lastNoteContent", lastNote);
        model.addAttribute("roomName", roomName);
        model.addAttribute("isAdmin", currentUser.isAdmin());
        model.addAttribute("jitsiJwt", jitsiJwt); // Відправляємо токен у фронтенд

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
}