package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordForm {

    @NotBlank(message = "Токен відновлення відсутній")
    private String token;

    @NotBlank(message = "Вкажіть новий пароль")
    @Size(min = 8, message = "Новий пароль має бути не менше 8 символів")
    private String newPassword;

    @NotBlank(message = "Підтвердіть новий пароль")
    private String confirmNewPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }

    public void setConfirmNewPassword(String confirmNewPassword) {
        this.confirmNewPassword = confirmNewPassword;
    }
}
