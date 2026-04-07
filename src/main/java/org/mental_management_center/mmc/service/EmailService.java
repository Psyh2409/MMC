package org.mental_management_center.mmc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String publicBaseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void sendVerificationEmail(String to, String token) {
        String subject = "Підтвердження реєстрації - Mental Management Center";
        String confirmationUrl = baseUrl() + "/registrationConfirm?token=" + token;

        String message = "Вітаємо! Для завершення реєстрації перейдіть за посиланням: \n" + confirmationUrl;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);

        mailSender.send(email);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String subject = "Відновлення пароля - Mental Management Center";
        String resetUrl = baseUrl() + "/reset-password?token=" + token;
        String message = "Щоб задати новий пароль, перейдіть за посиланням: \n" + resetUrl
                + "\n\nЯкщо ви не ініціювали відновлення пароля, просто проігноруйте цей лист.";

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);

        mailSender.send(email);
    }

    private String baseUrl() {
        return publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "http://localhost:8080"
                : publicBaseUrl.replaceAll("/+$", "");
    }
}
