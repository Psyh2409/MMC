package org.mental_management_center.mmc.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String token) {
        String subject = "Підтвердження реєстрації - Mental Management Center";
        // Поки що залишаємо localhost, пізніше замінимо на реальний домен
        String confirmationUrl = "http://localhost:8080/registrationConfirm?token=" + token;

        String message = "Вітаємо! Для завершення реєстрації перейдіть за посиланням: \n" + confirmationUrl;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);

        mailSender.send(email);
    }
}