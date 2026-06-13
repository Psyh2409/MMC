package org.mental_management_center.mmc.controller;

import org.junit.jupiter.api.Test;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void testForgotPasswordGet() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attributeExists("forgotPasswordForm"));
    }

    @Test
    void testForgotPasswordPostSuccess() throws Exception {
        // Given
        doNothing().when(userService).initiatePasswordReset(anyString());

        // When & Then
        mockMvc.perform(post("/forgot-password")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().attributeExists("message"))
                .andExpect(model().attribute("message",
                        "Якщо акаунт з таким email існує, ми надіслали інструкції для відновлення пароля."));
    }

    @Test
    void testForgotPasswordPostWithValidationError() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "") // Empty email
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(model().hasErrors());
    }
}