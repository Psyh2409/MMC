package org.mental_masochistic_club.mentalmasochisticclub.controller;

import org.mental_masochistic_club.mentalmasochisticclub.repository.RequestRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.mental_masochistic_club.mentalmasochisticclub.model.Request;
import org.mental_masochistic_club.mentalmasochisticclub.service.RequestService;

import java.time.LocalDateTime;
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
    public String showForm() {
        return "contact";
    }

    @PostMapping("/contact")
    public String submitForm(@ModelAttribute Request request) {
//        request.prePersist();
        service.save(request);
        return "redirect:/contact?success";
    }

    @GetMapping("/requests")
    public String showRequests(Model model) {
        List<Request> requests = requestRepository.findAll();
        System.out.println("Size of requests = " + requests.size());
        model.addAttribute("requests", requests);
        return "requests";
    }
}
