package org.mental_management_center.mmc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.VerificationTokenRepository;
import org.mental_management_center.mmc.service.EmailService;
import org.mental_management_center.mmc.service.UserService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileAndResetTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    private UserService userService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(
                userRepository,
                passwordEncoder,
                tokenRepository,
                emailService
        );
    }

    @Test
    void updateProfileNamePersistsTrimmedValue() {
        User user = new User();
        user.setEmail("reader@example.com");
        user.setName("Old Name");

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateProfileName("reader@example.com", "  New Name  ");

        assertTrue("New Name".equals(user.getName()));
        verify(userRepository).save(user);
    }

    @Test
    void initiatePasswordResetGeneratesTokenAndSendsEmail() {
        User user = new User();
        user.setEmail("reader@example.com");

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.initiatePasswordReset("reader@example.com");

        assertTrue(user.getPasswordResetToken() != null && !user.getPasswordResetToken().isBlank());
        assertTrue(user.getPasswordResetTokenExpiry() != null);
        verify(emailService).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void validPasswordResetTokenIsAccepted() {
        User user = new User();
        user.setPasswordResetToken("token-123");
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByPasswordResetToken("token-123")).thenReturn(Optional.of(user));

        assertTrue(userService.isPasswordResetTokenValid("token-123"));
    }

    @Test
    void expiredPasswordResetTokenIsRejected() {
        User user = new User();
        user.setPasswordResetToken("token-123");
        user.setPasswordResetTokenExpiry(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByPasswordResetToken("token-123")).thenReturn(Optional.of(user));

        assertFalse(userService.isPasswordResetTokenValid("token-123"));
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        User user = new User();
        user.setPassword(passwordEncoder.encode("oldPassword123"));
        user.setPasswordResetToken("token-123");
        user.setPasswordResetTokenExpiry(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByPasswordResetToken("token-123")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () ->
                userService.resetPassword("token-123", "newPassword123", "newPassword123"));
    }

    @Test
    void resetPasswordUpdatesPasswordAndClearsToken() {
        User user = new User();
        user.setPassword(passwordEncoder.encode("oldPassword123"));
        user.setPasswordResetToken("token-123");
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByPasswordResetToken("token-123")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.resetPassword("token-123", "newPassword123", "newPassword123");

        assertTrue(passwordEncoder.matches("newPassword123", user.getPassword()));
        assertTrue(user.getPasswordResetToken() == null);
        assertTrue(user.getPasswordResetTokenExpiry() == null);
        verify(userRepository).save(user);
    }
}
