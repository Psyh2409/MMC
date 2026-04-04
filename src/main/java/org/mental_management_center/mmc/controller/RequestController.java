package org.mental_management_center.mmc.controller;

import org.mental_management_center.mmc.repository.RequestRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.mental_management_center.mmc.model.Request;
import org.mental_management_center.mmc.service.RequestService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class RequestController {

    private final  RequestService service;
    private final RequestRepository requestRepository;


    public RequestController(RequestService service, RequestRepository requestRepository) {
        this.service = service;
        this.requestRepository = requestRepository;
    }

    @GetMapping("/contact")
    public String showForm(
            @RequestParam(value = "success", required = false)
            String success, Model model) {
        if (success != null)
            model.addAttribute(success, true);
        return "contact";
    }

    @PostMapping("/contact")
    public String submitForm(
            @ModelAttribute Request request, RedirectAttributes redirectAttributes) {
        service.save(request);
        redirectAttributes.addAttribute("success", true);
        return "redirect:/contact?success";
    }

    @GetMapping("/requests")
    public String showRequests(Model model) {
        List<Request> requests = requestRepository.findAll();
        System.out.println("Size of requests = " + requests.size());
        model.addAttribute("requests", requests);
        return "requests";
    }

    @GetMapping("/requests/delete/{id}")
    public String deleteRequest(@PathVariable Long id) {
        requestRepository.deleteById(id);
        return "redirect:/requests";
    }
}
