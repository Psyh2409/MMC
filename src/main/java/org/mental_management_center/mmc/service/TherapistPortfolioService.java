package org.mental_management_center.mmc.service;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.TherapistPortfolio;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.TherapistPortfolioRepository;
import org.mental_management_center.mmc.web.form.TherapistPortfolioFormDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TherapistPortfolioService {

    private final TherapistPortfolioRepository portfolioRepository;

    // 1. Читання: Ініціалізуємо ліниві колекції, щоб не було LazyInitializationException у Thymeleaf
    @Transactional(readOnly = true)
    public Optional<TherapistPortfolio> getPortfolioByUserId(UUID userId) {
        return portfolioRepository.findByUserId(userId);
    }

    // Оновлений метод мапінгу у DTO
    @Transactional(readOnly = true)
    public TherapistPortfolioFormDto getFormByUserId(UUID userId) {
        TherapistPortfolioFormDto form = new TherapistPortfolioFormDto();
        portfolioRepository.findByUserId(userId).ifPresent(p -> {
            form.setExperienceYears(p.getExperienceYears());
            form.setPracticeType(p.getPracticeType());
            form.setNonMedicalCompetenceAware(p.isNonMedicalCompetenceAware());
            form.setAboutMe(p.getAboutMe());
            form.setCredo(p.getCredo());
            form.setApproaches(joinSet(p.getApproaches()));
            form.setTargetIssues(joinSet(p.getTargetIssues()));
            form.setPrinciples(joinSet(p.getPrinciples()));
            form.setTechniques(joinSet(p.getTechniques()));
            form.setCertificates(joinSet(p.getCertificates()));
        });
        return form;
    }

    // Оновлений метод збереження
    @Transactional
    public void saveFromForm(User therapist, TherapistPortfolioFormDto form) {
        // Серверна валідація етики
        if ("NON_MEDICAL".equals(form.getPracticeType()) && !form.isNonMedicalCompetenceAware()) {
            throw new IllegalArgumentException("Необхідно підтвердити межі компетенції для немедичного фахівця.");
        }

        TherapistPortfolio portfolio = portfolioRepository.findByUserId(therapist.getId())
                .orElseGet(() -> TherapistPortfolio.builder().user(therapist).build());

        portfolio.setExperienceYears(form.getExperienceYears());
        portfolio.setPracticeType(form.getPracticeType());
        portfolio.setNonMedicalCompetenceAware(form.isNonMedicalCompetenceAware());
        portfolio.setAboutMe(form.getAboutMe());
        portfolio.setCredo(form.getCredo());
        portfolio.setApproaches(parseToSet(form.getApproaches()));
        portfolio.setTargetIssues(parseToSet(form.getTargetIssues()));
        portfolio.setPrinciples(parseToSet(form.getPrinciples()));
        portfolio.setTechniques(parseToSet(form.getTechniques()));
        portfolio.setCertificates(parseToSet(form.getCertificates()));

        portfolioRepository.save(portfolio);
    }

    // --- Допоміжні методи для конвертації ---

    // Розбиває рядок з форми (через кому або Enter) у безпечну колекцію
    private Set<String> parseToSet(String input) {
        if (input == null || input.isBlank()) return new HashSet<>();
        return Arrays.stream(input.split("[,\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // Склеює колекцію з БД у рядок через кому для відображення в input
    private String joinSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        return String.join(", ", set);
    }
}