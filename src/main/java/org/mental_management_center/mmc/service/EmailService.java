package org.mental_management_center.mmc.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String adminMail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
        this.adminMail = "mental.m.center@gmail.com";
        logger.info("EmailService initialized with publicBaseUrl: {}", publicBaseUrl);
        logger.info("JavaMailSender: {}", mailSender != null ? "initialized" : "null");
        
        // Логуємо mail конфігурацію для діагностики
        logger.info("Mail configuration - host: smtp.gmail.com, port: 587");
    }

    public void sendVerificationEmail(String to, String token) {
        try {
            String subject = "Підтвердження реєстрації - Mental Management Center";
            String confirmationUrl = baseUrl() + "/registrationConfirm?token=" + token;

            String message = "Вітаємо! Для завершення реєстрації перейдіть за посиланням: \n" + confirmationUrl;

            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(to);
            email.setSubject(subject);
            email.setText(message);

            logger.info("Sending verification email to: {}", to);
            mailSender.send(email);
            logger.info("Verification email sent successfully to: {}", to);
        } catch (MailException e) {
            logger.error("Failed to send verification email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Не вдалося відправити лист підтвердження: " + e.getMessage(), e);
        }
    }

    public void sendPasswordResetEmail(String to, String token) {
        try {
            logger.info("=== STARTING PASSWORD RESET EMAIL PROCESS ===");
            logger.info("Recipient: {}", to);
            logger.info("Token: {}", token);
            
            String subject = "Відновлення пароля - Mental Management Center";
            String resetUrl = baseUrl() + "/reset-password?token=" + token;
            String message = "Щоб задати новий пароль, перейдіть за посиланням: \n" + resetUrl
                    + "\n\nЯкщо ви не ініціювали відновлення пароля, просто проігноруйте цей лист.";

            logger.info("Subject: {}", subject);
            logger.info("Reset URL: {}", resetUrl);
            logger.info("Message length: {} characters", message.length());

            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(to);
            email.setSubject(subject);
            email.setText(message);
            email.setFrom(adminMail); // Явно вказуємо відправника

            logger.info("Sending password reset email to: {}", to);
            logger.info("From: "+ adminMail);
            
            mailSender.send(email);
            
            logger.info("=== PASSWORD RESET EMAIL SENT SUCCESSFULLY ===");
            logger.info("Email sent to: {}", to);
        } catch (MailException e) {
            logger.error("=== PASSWORD RESET EMAIL FAILED ===");
            logger.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
            logger.error("Exception type: {}", e.getClass().getName());
            
            // Логуємо більше деталей про помилку
            Throwable cause = e.getCause();
            if (cause != null) {
                logger.error("Cause: {}", cause.getMessage());
                logger.error("Cause type: {}", cause.getClass().getName());
            }
            
            throw new RuntimeException("Не вдалося відправити лист відновлення пароля: " + e.getMessage(), e);
        }
    }

    private String baseUrl() {
        return publicBaseUrl == null || publicBaseUrl.isBlank()
//                ? "https://corridor-uninsured-fang.ngrok-free.dev"
                ? "http://localhost:8080"
                : publicBaseUrl.replaceAll("/+$", "");
    }

    public void sendSupportReply(String to, String userName, String originalMessage, String replyMessage) {
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setTo(to);
            email.setSubject("Відповідь на ваше звернення — Mental Management Center");

            // Формуємо чітку структуру: Спочатку цитата, потім твоя відповідь
            String body = String.format(
                    "Вітаємо, %s!\n\n" +
                            "Ви залишали звернення на платформі Mental Management Center.\n\n" +
                            "--- ВАШЕ ЗАПИТАННЯ ---\n" +
                            "\"%s\"\n" +
                            "------------------------\n\n" +
                            "--- ВІДПОВІДЬ ФАХІВЦЯ ---\n" +
                            "%s\n" +
                            "-------------------------\n\n" +
                            "З повагою,\n" +
                            "Команда Mental Management Center",
                    userName != null ? userName : "користувачу",
                    originalMessage,
                    replyMessage
            );

            email.setText(body);
            email.setFrom(adminMail);

            mailSender.send(email);
            logger.info("Відповідь із цитатою успішно надіслана на пошту: {}", to);
        } catch (MailException e) {
            logger.error("Не вдалося відправити лист: {}", e.getMessage());
            throw new RuntimeException("Помилка відправки SMTP: " + e.getMessage(), e);
        }
    }

    /**
     * Автоматичний лист-підтвердження для клієнта, що його запит успішно отримано
     */
    public void sendAutoAcknowledgement(String clientEmail, String clientName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(adminMail); // Твоя системна пошта MMC
            helper.setTo(clientEmail);
            helper.setSubject("🌿 Mental Management Center — Ваше звернення отримано");

            // Формуємо чистий, спокійний текст листа без жодного HTML-сміття
            String text = String.format(
                    "Вітаємо, %s!\n\n" +
                            "Цей лист підтверджує, що ваше повідомлення успішно доставлено до Mental Management Center.\n" +
                            "Ми розуміємо важливість вашого звернення. Наш спеціаліст опрацює його та надасть відповідь найближчим часом.\n\n" +
                            "Будь ласка, не відповідайте на цей лист, він згенерований автоматично.\n\n" +
                            "З повагою,\nКоманда Mental Management Center 🌿",
                    clientName
            );

            helper.setText(text, false); // false означає чистий plain text без кривих стилів
            mailSender.send(message);
        } catch (Exception e) {
            // Логуємо помилку, але не ламаємо відправку самої форми, якщо впав поштовий сервер
            logger.error("Не вдалося надіслати автопідтвердження клієнту: {}", e.getMessage());
        }
    }
}
