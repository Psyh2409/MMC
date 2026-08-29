package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.SiteStats;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.model.enums.RoleBit;
import org.mental_management_center.mmc.repository.ArticleRepository;
import org.mental_management_center.mmc.repository.PublicPostRepository;
import org.mental_management_center.mmc.repository.SiteStatsRepository;
import org.mental_management_center.mmc.repository.TherapistPortfolioRepository;
import org.mental_management_center.mmc.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PublicPostRepository publicPostRepository;
    private final ArticleRepository articleRepository;
    private final TherapistPortfolioRepository portfolioRepository;
    private final UserService userService;
    private final SiteStatsRepository siteStatsRepository;

    @GetMapping("/")
    public String index(Principal principal, Model model) {
        boolean isTestUser = false;

        if (principal == null) {
            // 1. Логіка для гостей: лічильник відвідувань
            UUID statsId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            SiteStats siteStats = siteStatsRepository.findById(statsId).orElseGet(SiteStats::new);
            siteStats.setGuestVisits(siteStats.getGuestVisits() + 1);
            model.addAttribute("guestCount", siteStats.getGuestVisits());
            siteStatsRepository.save(siteStats);
        } else {
            // 2. Логіка для авторизованих користувачів
            User currentUser = userService.findByEmail(principal.getName()).orElse(null);
            if (currentUser != null) {
                isTestUser = currentUser.isTest();
                model.addAttribute("userName", currentUser.getName());

                // Обчислення пріоритетної ролі для UI
                RoleBit priorityRole = RoleBit.READER;
                if (currentUser.isAdmin()) {
                    priorityRole = RoleBit.ADMIN;
                } else if (currentUser.isTherapist()) {
                    priorityRole = RoleBit.THERAPIST;
                } else if (currentUser.isClient()) {
                    priorityRole = RoleBit.CLIENT;
                }
                model.addAttribute("userRole", priorityRole.name());
            }
        }

        // 3. Вибірка контенту для Landing Page
        // Стрічка постів (10 елементів, Slice з JOIN FETCH для економії RAM)
        PageRequest postPageable = PageRequest.of(0, 10);
        var postsSlice = isTestUser
                ? publicPostRepository.findLatestTestPosts(postPageable)
                : publicPostRepository.findLatestRealPosts(postPageable);

        // Права панель: TOP-3 статті
        var topArticles = articleRepository.findTop3ByOrderByPublishedAtDesc();

        // Права панель: TOP-3 верифікованих фахівці
        var topSpecialists = portfolioRepository.findTop3Specialists(PageRequest.of(0, 3));

        model.addAttribute("postsSlice", postsSlice);
        model.addAttribute("topArticles", topArticles);
        model.addAttribute("topSpecialists", topSpecialists);

        return "index";
    }

    @GetMapping("/api/public/posts")
    public String getMorePosts(@RequestParam(defaultValue = "1") int page, Principal principal, Model model) {
        boolean isTestUser = false;

        if (principal != null) {
            User currentUser = userService.findByEmail(principal.getName()).orElse(null);
            if (currentUser != null && currentUser.isTest()) {
                isTestUser = true;
            }
        }

        PageRequest pageable = PageRequest.of(page, 10);
        var postsSlice = isTestUser
                ? publicPostRepository.findLatestTestPosts(pageable)
                : publicPostRepository.findLatestRealPosts(pageable);

        model.addAttribute("postsSlice", postsSlice);
        return "fragments/post-items :: post-list";
    }
}