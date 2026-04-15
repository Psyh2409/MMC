package org.mental_management_center.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    // Усі методи, що повертають HTML-шаблони, живуть тут, на самому верху.

    @GetMapping("/chat")
    public String chatPage() {
        return "chat"; // Повертає шаблон chat.html
    }

    // В майбутньому сюди можна додати:
    // @GetMapping("/about")
    // @GetMapping("/contacts")
}