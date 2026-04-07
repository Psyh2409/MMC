package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeForm {

    @NotBlank(message = "Вкажіть поточний пароль")
    private String currentPassword;

    @NotBlank(message = "Вкажіть новий пароль")
    @Size(min = 8, message = "Новий пароль має бути не менше 8 символів")
    private String newPassword;

    @NotBlank(message = "Підтвердіть новий пароль")
    private String confirmNewPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
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
