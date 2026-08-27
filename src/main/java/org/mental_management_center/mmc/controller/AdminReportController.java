package org.mental_management_center.mmc.controller;

import lombok.RequiredArgsConstructor;
import org.mental_management_center.mmc.model.Report;
import org.mental_management_center.mmc.repository.ChatMessageRepository;
import org.mental_management_center.mmc.repository.CommentRepository;
import org.mental_management_center.mmc.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN') and !hasRole('TEST')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportRepository reportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    @GetMapping
    public String listReports(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Page<Report> reportsPage = reportRepository.findByStatusOrderByCreatedAtDesc(
                Report.ReportStatus.PENDING, PageRequest.of(page, 10));

        Map<UUID, String> targetPreviews = new HashMap<>();

        for (Report report : reportsPage.getContent()) {
            if (report.getTargetType() == Report.TargetType.CHAT_MESSAGE) {
                chatMessageRepository.findById(report.getTargetId())
                        .ifPresent(msg -> targetPreviews.put(report.getId(), msg.getContent()));
            } else if (report.getTargetType() == Report.TargetType.COMMENT) {
                commentRepository.findById(report.getTargetId())
                        .ifPresent(c -> targetPreviews.put(report.getId(), c.getContent()));
            }
        }

        model.addAttribute("reports", reportsPage.getContent());
        model.addAttribute("previews", targetPreviews);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", reportsPage.getTotalPages());

        return "admin-reports";
    }

    @Transactional
    @PostMapping("/{id}/approve")
    public String approveReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String adminComment) {

        Report report = reportRepository.findById(id).orElseThrow();

        if (report.getReportType() == Report.ReportType.COMPLAINT) {
            applySoftDelete(report.getTargetType(), report.getTargetId(), report.getReason());
        } else if (report.getReportType() == Report.ReportType.APPEAL) {
            restoreContent(report.getTargetType(), report.getTargetId());
        }

        report.setStatus(Report.ReportStatus.APPROVED);
        report.setResolvedAt(LocalDateTime.now());
        report.setAdminComment(adminComment);
        reportRepository.save(report);

        return "redirect:/admin/reports";
    }

    @Transactional
    @PostMapping("/{id}/reject")
    public String rejectReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String adminComment) {

        Report report = reportRepository.findById(id).orElseThrow();

        report.setStatus(Report.ReportStatus.REJECTED);
        report.setResolvedAt(LocalDateTime.now());
        report.setAdminComment(adminComment);
        reportRepository.save(report);

        return "redirect:/admin/reports";
    }

    private void applySoftDelete(Report.TargetType targetType, UUID targetId, String reason) {
        if (targetType == Report.TargetType.CHAT_MESSAGE) {
            chatMessageRepository.findById(targetId).ifPresent(msg -> {
                msg.setDeletedByAdmin(true);
                msg.setDeletionReason(reason);
                chatMessageRepository.save(msg);
            });
        } else if (targetType == Report.TargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                comment.setDeletedByAdmin(true);
                comment.setDeletionReason(reason);
                commentRepository.save(comment);
            });
        }
    }

    private void restoreContent(Report.TargetType targetType, UUID targetId) {
        if (targetType == Report.TargetType.CHAT_MESSAGE) {
            chatMessageRepository.findById(targetId).ifPresent(msg -> {
                msg.setDeletedByAdmin(false);
                msg.setDeletionReason(null);
                chatMessageRepository.save(msg);
            });
        } else if (targetType == Report.TargetType.COMMENT) {
            commentRepository.findById(targetId).ifPresent(comment -> {
                comment.setDeletedByAdmin(false);
                comment.setDeletedByAuthor(false);
                comment.setDeletionReason(null);
                commentRepository.save(comment);
            });
        }
    }
}