package org.mental_management_center.mmc.service;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
class WebAccessIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicPagesRemainAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/about"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/issues/inner-calm"))
                .andExpect(status().isOk())
                .andExpect(view().name("issues/inner-calm"));

        mockMvc.perform(get("/issues/not-existing-topic"))
                .andExpect(status().isOk())
                .andExpect(view().name("issues/default"));

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(view().name("articles"));
    }

    @Test
    void contactFormAcceptsAnonymousSubmissionWithCsrf() throws Exception {
                mockMvc.perform(post("/contact")
                    .with(csrf())
                        .param("name", "Test User")
                        .param("contact", "test@example.com")
                        .param("message", "Need help"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contact?success"));
    }

    @Test
    void requestsPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void profilePageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void forgotPasswordPageIsAccessible() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"));
    }

    @Test
    @WithMockUser(roles = "READER")
    void missingPageUsesCustomErrorView() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }

    @Test
    @WithMockUser(roles = "READER")
    void requestsPageIsForbiddenForNonAdmins() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requestsPageIsAvailableForAdmins() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isOk())
                .andExpect(view().name("requests"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mutatingEndpointsRequireCsrfToken() throws Exception {
        UUID randomRequestId = UUID.randomUUID();
        mockMvc.perform(post("/requests/delete/" + randomRequestId))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/requests/delete/" + randomRequestId).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    void passwordChangeEndpointRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/profile/password")
                        .param("currentPassword", "oldPassword123")
                        .param("newPassword", "newPassword123")
                        .param("confirmNewPassword", "newPassword123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    void profileUpdateEndpointRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/profile")
                        .param("name", "Updated Name"))
                .andExpect(status().isForbidden());
    }
}
