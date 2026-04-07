package org.mental_management_center.mmc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.UserRepository;
import org.mental_management_center.mmc.repository.VerificationTokenRepository;
import org.mental_management_center.mmc.service.EmailService;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceOAuthVerificationTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                new BCryptPasswordEncoder(),
                tokenRepository,
                emailService
        );
    }

    @Test
    void newOAuthUserMustRemainDisabledUntilEmailVerification() {
        User userToSave = new User();

        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(1L);
            }
            return user;
        });
        when(tokenRepository.findByUser(any(User.class))).thenReturn(Optional.empty());

        User savedUser = userService.upsertOAuth2User("oauth@example.com", "OAuth User", "GOOGLE", "provider-id");

        assertNotNull(savedUser);
        assertFalse(savedUser.isEnabled());
        verify(tokenRepository).save(any());
        verify(emailService).sendVerificationEmail(eq("oauth@example.com"), any(String.class));
    }

    @Test
    void enabledExistingOAuthUserDoesNotReceiveNewVerificationEmail() {
        User existingUser = new User();
        existingUser.setEmail("oauth@example.com");
        existingUser.setEnabled(true);

        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.upsertOAuth2User("oauth@example.com", "OAuth User", "GOOGLE", "provider-id");

        verify(emailService, never()).sendVerificationEmail(any(String.class), any(String.class));
    }
}
