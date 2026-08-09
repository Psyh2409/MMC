package org.mental_management_center.mmc.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Report;
import org.mental_management_center.mmc.model.User;
import org.mental_management_center.mmc.repository.ReportRepository;
import org.mental_management_center.mmc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createReport(@RequestBody ReportRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();

        // Захист від спаму: перевіряємо, чи вже є активна скарга від цього юзера
        boolean exists = reportRepository.existsByReporterIdAndTargetIdAndStatus(
                currentUser.getId(), request.getTargetId(), Report.ReportStatus.PENDING);

        if (exists) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Скарга вже існує");
        }

        Report report = Report.builder()
                .reporter(currentUser)
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .reportType(Report.ReportType.COMPLAINT)
                .reason(request.getReason())
                .status(Report.ReportStatus.PENDING)
                .build();

        reportRepository.save(report);

        return ResponseEntity.ok().build();
    }

    @Data
    public static class ReportRequest {
        private UUID targetId;
        private Report.TargetType targetType;
        private String reason;
    }
}