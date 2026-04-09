package org.mental_management_center.mmc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/issues")
public class IssueController {

    private static final Map<String, String> TOPICS = Map.of(
            "inner-calm", "Тривога та панічні стани",
            "restore-resource", "Вигорання та відновлення ресурсу",
            "dialogue", "Конфлікти та медіація",
            "closeness-crisis", "Стосунки та кризи близькості",
            "new-meanings", "Депресивні стани та пошук сенсів",
            "freedom-choice", "Залежні форми поведінки",
            "exit-nearby", "Ілюзія, що виходу нема");

    @GetMapping("/{topic}")
    public String getIssuePage(@PathVariable String topic, Model model) {
        // 1. Шукаємо заголовок у мапі за ключем з URL
        String ukrainianTitle = TOPICS.get(topic);

        // 2. Якщо такого ключа немає (користувач помилився в URL)
        if (ukrainianTitle == null) {
            model.addAttribute("topicTitle", "Психологічна допомога");
            return "issues/default";
        }

        // 3. Якщо ключ є — передаємо заголовок у модель
        model.addAttribute("topicTitle", ukrainianTitle);

        // 4. Повертаємо назву шаблону, яка збігається з ключем (наприклад,
        // issues/inner-calm.html)
        return "issues/" + topic;
    }
}
