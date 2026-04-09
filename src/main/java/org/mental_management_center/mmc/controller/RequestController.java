package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.service.RequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Controller
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/contact")
    public String showForm(Model model) {
        // Форма тепер чиста, success ми обробляємо через параметри в HTML
        return "contact";
    }

    @PostMapping("/contact")
    public String submitForm(@ModelAttribute Request request, Principal principal) {
        requestService.save(request, principal);
        return "redirect:/contact?success";
    }

    @GetMapping("/requests")
    public String showRequests(Model model) {
        model.addAttribute("requests", requestService.findAllNewestFirst());
        return "requests";
    }

    @PostMapping("/requests/delete/{id}")
    public String deleteRequest(@PathVariable UUID id) {
        requestService.deleteById(id);
        return "redirect:/requests";
    }
}
