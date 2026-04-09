package org.mental_management_center.mmc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void testSendPasswordResetEmail() {
        // Given
        EmailService emailService = new EmailService(mailSender, "http://localhost:8080");

        // When
        emailService.sendPasswordResetEmail("test@example.com", "test-token");

        // Then
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendVerificationEmail() {
        // Given
        EmailService emailService = new EmailService(mailSender, "http://localhost:8080");

        // When
        emailService.sendVerificationEmail("test@example.com", "test-token");

        // Then
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}