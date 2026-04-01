package org.mental_masochistic_club.mmc.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EncounterController {

    @GetMapping("/format")
    public String showManifestoPage() {
        // Spring шукатиме файл з назвою honestly.html у папці resources/templates
        return "my-format";
    }
}
