package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TherapistPortfolioFormDto {

    @Min(value = 0, message = "Досвід не може бути від'ємним")
    private Integer experienceYears;

    @Size(max = 1000, message = "Кредо не повинно перевищувати 1000 символів")
    private String credo;

    private String approaches;    // Введення через кому або новий рядок
    private String targetIssues;  // Введення через кому або новий рядок
    private String principles;    // Введення через кому або новий рядок
    private String techniques;    // Введення через кому або новий рядок
    private String certificates;  // Введення через кому або новий рядок
    @NotBlank(message = "Оберіть тип психотерапевтичної практики")
    private String practiceType;

    private boolean nonMedicalCompetenceAware;

    @Size(max = 1500, message = "Опис 'Про себе' не може перевищувати 1500 символів")
    private String aboutMe;
}