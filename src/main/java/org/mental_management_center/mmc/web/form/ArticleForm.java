package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
public class ArticleForm {

    private UUID id; // ДОДАТИ: Якщо id є - це редагування. Якщо null - це нова стаття.

    @NotBlank(message = "Заголовок не може бути порожнім")
    private String title;

    private String description;

    @NotBlank(message = "Категорія обов'язкова")
    private String category;

    @NotBlank(message = "Назва українською не може бути порожньою")// ДОДАЄМО: Для української назви нової категорії
    private String categoryNameUa;

    private String tags; // Рядок, який ми потім розіб'ємо по комі

    @NotBlank(message = "Текст статті не може бути порожнім")
    private String content;
}