package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordForm {

    @NotBlank(message = "Email не може бути порожнім")
    @Email(message = "Невірний формат Email")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
