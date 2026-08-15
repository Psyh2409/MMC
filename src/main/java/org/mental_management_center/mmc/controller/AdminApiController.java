package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Report;
import org.mental_management_center.mmc.repository.ReportRepository;
import org.mental_management_center.mmc.repository.RequestRepository;
import org.mental_management_center.mmc.repository.SpecialistAppRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final ReportRepository reportRepository;
    private final RequestRepository requestRepository;
    private final SpecialistAppRepository specialistAppRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/alerts-count")
    public ResponseEntity<Long> getAdminAlertsCount() {
        long pendingReports = reportRepository.countByStatus(Report.ReportStatus.PENDING);
        long pendingRequests = requestRepository.countUnprocessedAdminRequests();
        long pendingSpecialists = specialistAppRepository.countByStatus("PENDING");
        long sosRequests = userRepository.countBySosRequestedTrue();

        // Сумуємо всі події
        long totalAlerts = pendingReports + pendingRequests + pendingSpecialists + sosRequests;

        return ResponseEntity.ok(totalAlerts);
    }
}