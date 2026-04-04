package org.mental_management_center.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ContentController {

    @GetMapping("/first-steps")
    public  String firstSteps() {
        return "first-steps";
    }

    @GetMapping("/about")
    public  String about() {
        return "about";
    }

    @GetMapping("/articles")
    public  String articles() {
        return "articles";
    }

}
