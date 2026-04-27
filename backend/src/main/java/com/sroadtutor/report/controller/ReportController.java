package com.sroadtutor.report.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.report.service.PdfReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * PDF report-card downloads. Streams {@code application/pdf} with a
 * sensible {@code Content-Disposition} so a browser opens or saves the
 * file with a recognisable name.
 *
 * <p>Scope is enforced by {@link PdfReportService} via the underlying
 * {@code MistakeLogService} / {@code PaymentService} calls — those throw
 * 403 if the caller can't read the data.</p>
 */
@RestController
@RequestMapping("/api/students")
@Tag(name = "Reports", description = "Per-student PDF report cards")
public class ReportController {

    private final PdfReportService service;

    public ReportController(PdfReportService service) {
        this.service = service;
    }

    @GetMapping(value = "/{id}/report-card.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate the student's PDF report card. Same scope as readiness/ledger reads.")
    public ResponseEntity<byte[]> reportCard(@PathVariable UUID id) {
        byte[] bytes = service.buildStudentReport(
                SecurityUtil.currentRole(),
                SecurityUtil.currentUserId(),
                id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "report-card-" + id + ".pdf");
        return new ResponseEntity<>(bytes, headers, 200);
    }
}
