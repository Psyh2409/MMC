package org.mental_management_center.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/issues")
public class IssueController {

    // Список дозволених тем, щоб уникнути помилок при спробі відкрити неіснуючий файл
    private final List<String> allowedTopics = Arrays.asList(
            "inner-calm", "restore-resource", "dialogue", "closeness-crisis", "new-meanings", "freedom-choice", "exit-nearby"
    );

    @GetMapping("/{topic}")
    public String getIssuePage(@PathVariable String topic, Model model) {
        if (!allowedTopics.contains(topic)) {
            model.addAttribute("topicTitle", "Психологічна допомога");
            return "issues/default";
        }

        String ukrainianTitle;
        switch (topic) {
            case "inner-calm":
                ukrainianTitle = "Тривога та панічні стани";
                break;
            case "restore-resource":
                ukrainianTitle = "Вигорання та відновлення ресурсу";
                break;
            case "dialogue":
                ukrainianTitle = "Конфлікти та медіація";
                break;
            case "closeness-crisis":
                ukrainianTitle = "Стосунки та кризи близькості";
                break;
            case "new-meanings":
                ukrainianTitle = "Депресивні стани та пошук сенсів";
                break;
            case "freedom-choice":
                ukrainianTitle = "Залежні форми поведінки";
                break;
            case "exit-nearby":
                ukrainianTitle = "Ілюзія, що виходу нема";
                break;
            default:
                ukrainianTitle = "Психологічна допомога";
                break;
        }
        model.addAttribute("topicTitle", ukrainianTitle);
        return "issues/" + topic;
    }
}
