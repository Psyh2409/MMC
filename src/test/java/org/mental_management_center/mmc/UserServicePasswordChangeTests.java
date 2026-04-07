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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordChangeTests {

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
    void changePasswordRejectsWrongCurrentPassword() {
        User user = new User();
        user.setEmail("reader@example.com");
        user.setPassword(passwordEncoder.encode("oldPassword123"));

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () ->
                userService.changePassword("reader@example.com", "wrongPassword", "newPassword123", "newPassword123"));
    }

    @Test
    void changePasswordPersistsEncodedPasswordForValidRequest() {
        User user = new User();
        user.setEmail("reader@example.com");
        user.setPassword(passwordEncoder.encode("oldPassword123"));

        when(userRepository.findByEmail("reader@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword("reader@example.com", "oldPassword123", "newPassword123", "newPassword123");

        verify(userRepository).save(user);
    }
}
