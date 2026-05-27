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
        model.addAttribute("request", new Request());
        return "contact";
    }

    @PostMapping("/contact")
    public String submitForm(@ModelAttribute Request request, Principal principal) {
        String contact = request.getContact();

        if (contact != null) {
            // Відрізаємо випадкові пробіли
            request.setContact(contact.trim());
        }

        // Зберігаємо в БД (твоя логіка в RequestService сама підтягне ім'я юзера, якщо він авторизований)
        requestService.save(request, principal);

        // Відправляємо автопідтвердження.
        // Замість старих методів використовуємо чисті поля, які вже точно є в об'єкті:
        emailService.sendAutoAcknowledgement(request.getContact(), request.getName());

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
    public String replyToRequest(@PathVariable UUID id, @RequestParam ("replyMessage") String replyMessage, Principal principal) {
        // 1. Отримуємо заявку з бази
        Request request = requestService.findById(id);

        // 2. Оновлюємо статус та зберігаємо відповідь в архіве платформи
        request.setStatus(RequestStatus.ANSWERED);
        request.setAdminReply(replyMessage);
        requestService.save(request, principal); // Переконайся, що метод save або update оновлює існуючий запис

        // 3. МАРШРУТИЗАЦІЯ КАНАЛІВ ЗВ'ЯЗКУ
        if (request.getContact() != null && !request.getContact().isBlank()) {
            // Канал пошти
            emailService.sendSupportReply(
                    request.getContact(),
                    request.getName(),
                    request.getMessage(),
                    replyMessage
            );
        }  else {
            throw new IllegalArgumentException("Неможливо надіслати відповідь: контактні дані відсутні.");
        }

        return "redirect:/requests";
    }
}
