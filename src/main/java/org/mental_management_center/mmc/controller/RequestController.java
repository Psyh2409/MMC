package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.RequestRepository;
import org.mental_management_center.mmc.repository.UserRepository; // Додав репозиторій юзерів
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal; // Важливо для ідентифікації
import java.util.List;
import java.util.Optional;

@Controller
public class RequestController {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository; // Потрібен для пошуку юзера

    public RequestController(RequestRepository requestRepository, UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/contact")
    public String showForm(Model model) {
        // Форма тепер чиста, success ми обробляємо через параметри в HTML
        return "contact";
    }

    @PostMapping("/contact")
    public String submitForm(@ModelAttribute Request request,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {

        if (principal != null) {
            // СЦЕНАРІЙ 1: Користувач залогінений
            Optional<User> userOpt = userRepository.findByEmail(principal.getName());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                request.setUser(user);
                request.setName(user.getName()); // Автозаповнення
                request.setContact(user.getEmail()); // Автозаповнення
                request.setRolesMask(user.getRolesMask());
            }
        } else {
            // СЦЕНАРІЙ 2: Гість або ЗАБЛОКОВАНИЙ (пише вручну)
            // Шукаємо, чи є в базі людина з такою поштою
            Optional<User> userByEmail = userRepository.findByEmail(request.getContact());
            if (userByEmail.isPresent()) {
                User user = userByEmail.get();
                request.setUser(user);
                request.setRolesMask(user.getRolesMask()); // Ми впізнали його навіть без логіна!
            } else {
                request.setRolesMask((byte) 1); // Просто анонімний гість
            }
        }

        requestRepository.save(request);
        return "redirect:/contact?success";
    }

    @GetMapping("/requests")
    public String showRequests(Model model) {
        List<Request> requests = requestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("requests", requests);
        return "requests";
    }

    @PostMapping("/requests/delete/{id}")
    public String deleteRequest(@PathVariable Long id) {
        requestRepository.deleteById(id);
        return "redirect:/requests";
    }
}