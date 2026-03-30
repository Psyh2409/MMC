package org.mental_masochistic_club.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/issues")
public class IssueController {

    @GetMapping("/{category}")
    public String getIssuePage(@PathVariable String category, Model model) {
        model.addAttribute("categoryName", formatCategoryName(category));
        return "issues/topic-layout";
    }

    private String formatCategoryName(String category) {
        return switch (category) {
            case  "anxiety" ->  "Тривога та панічні атаки";
            case  "depression" ->  "Депресія та апатія";
            case  "burnout" ->  "Професійне вигорання";
            default -> "Психологічна допомога";
        };
    }
}
