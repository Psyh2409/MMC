package org.mental_management_center.mmc.service;

import org.junit.jupiter.api.Test;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.VerificationTokenRepository;
import org.mental_management_center.mmc.service.EmailService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    @Autowired
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private VerificationTokenRepository tokenRepository;

    @MockBean
    private EmailService emailService;

    @Test
    void testPasswordResetFlowWithExistingUser() {
        // Given
        User existingUser = new User();
        existingUser.setEmail("test@example.com");
        existingUser.setName("Test User");
        existingUser.setPassword("oldPassword");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        assertDoesNotThrow(() -> userService.initiatePasswordReset("test@example.com"));

        // Then
        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository).save(existingUser);
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), anyString());

        // Verify token was set
        assertNotNull(existingUser.getPasswordResetToken());
        assertNotNull(existingUser.getPasswordResetTokenExpiry());
    }

    @Test
    void testPasswordResetFlowWithNonExistingUser() {
        // Given
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // When & Then
        assertDoesNotThrow(() -> userService.initiatePasswordReset("nonexistent@example.com"));

        // Verify no interactions with save or email service
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void testTokenValidation() {
        // Given
        User user = new User();
        user.setPasswordResetToken("valid-token");
        user.setPasswordResetTokenExpiry(java.time.LocalDateTime.now().plusHours(1));

        when(userRepository.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(user));

        // When & Then
        assertTrue(userService.isPasswordResetTokenValid("valid-token"));
    }

    @Test
    void testExpiredTokenValidation() {
        // Given
        User user = new User();
        user.setPasswordResetToken("expired-token");
        user.setPasswordResetTokenExpiry(java.time.LocalDateTime.now().minusHours(1));

        when(userRepository.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

        // When & Then
        assertFalse(userService.isPasswordResetTokenValid("expired-token"));
    }
}