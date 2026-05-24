package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.model.RequestStatus;
import org.mental_management_center.mmc.service.EmailService;
import org.mental_management_center.mmc.service.RequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@Controller
public class RequestController {

    private final RequestService requestService;
    private final EmailService emailService;


    public RequestController(RequestService requestService, EmailService emailService) {
        this.requestService = requestService;
        this.emailService = emailService;
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

    @GetMapping("/requests/{id}/reply")
    public String showReplyPage(@PathVariable UUID id, Model model) {
        model.addAttribute("request", requestService.findById(id));
        return "request-reply";
    }

    @PostMapping("/requests/{id}/reply")
    public String replyToRequest(@PathVariable UUID id, @RequestParam("replyMessage") String replyMessage) {
        // 1. Безпечно зберігаємо відповідь в БД
        requestService.saveAdminReply(id, replyMessage);

        // 2. Дістаємо запит для відправки листа
        Request request = requestService.findById(id);

        if (request.getEmailContact() != null && !request.getEmailContact().isBlank()) {
            emailService.sendSupportReply(
                    request.getEmailContact(),
                    request.getSenderName(),
                    request.getMessage(),
                    replyMessage);
        }

        return "redirect:/requests?success";
    }
}
