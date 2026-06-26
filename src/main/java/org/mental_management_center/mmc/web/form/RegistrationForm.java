package org.mental_management_center.mmc.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Ім'я не може бути порожнім")
    private String name;

    @NotBlank(message = "Email не може бути порожнім")
    @Email(message = "Невірний формат Email")
    private String email;

    @NotBlank(message = "Пароль не може бути порожнім")
    @Size(min = 8, message = "Пароль має бути не менше 8 символів")
    private String password;

    @NotBlank(message = "Підтвердження пароля є обов'язковим")
    private String confirmPassword;

    // Прапорець, який прилетить з фронтенду
    private boolean registerAsSpecialist;

    // Геттер та Сеттер для Spring, щоб він міг записати і прочитати значення
    public boolean isRegisterAsSpecialist() {
        return registerAsSpecialist;
    }

    public void setRegisterAsSpecialist(boolean registerAsSpecialist) {
        this.registerAsSpecialist = registerAsSpecialist;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
