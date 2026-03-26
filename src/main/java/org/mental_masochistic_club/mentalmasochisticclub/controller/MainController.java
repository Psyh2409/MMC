package org.mental_masochistic_club.mentalmasochisticclub.controller;

import org.springframework.stereotype.Controller;
import org. springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("message",
                "Привіт, це мій перший сайт на спрінгу");
        return "index";
    }
}
