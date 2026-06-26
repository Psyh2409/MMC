package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdeboVerificationForm {

    @NotBlank(message = "Оберіть рівень освіти")
    private String educationLevel;

    @NotBlank(message = "Серія диплома обов'язкова")
    private String diplomaSeries;

    @NotBlank(message = "Номер диплома обов'язковий")
    private String diplomaNumber;

    @NotBlank(message = "Прізвище обов'язкове")
    private String lastName;

    @NotBlank(message = "Ім'я обов'язкове")
    private String firstName;

    private String middleName;

    private boolean noMiddleName; // Галочка "По батькові відсутнє"
}