package com.sroadtutor.payment.controller;

import com.sroadtutor.auth.security.SecurityUtil;
import com.sroadtutor.common.ApiResponse;
import com.sroadtutor.payment.dto.MarkPaidRequest;
import com.sroadtutor.payment.dto.PaymentResponse;
import com.sroadtutor.payment.dto.RecordPaymentRequest;
import com.sroadtutor.payment.dto.StudentLedgerResponse;
import com.sroadtutor.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment ledger endpoints.
 *
 * <p>Auto-creation at session-COMPLETE happens inside
 * {@link com.sroadtutor.session.service.SessionService} — no public endpoint
 * for that. This controller exposes manual record + mark-paid + reads.</p>
 */
@RestController
@Tag(name = "Payments", description = "Payment ledger — manual record, mark paid, student ledger, school outstanding")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/api/payments")
    @Operation(summary = "Record a manual payment (CASH / ETRANSFER / OTHER). OWNER or assigned INSTRUCTOR.")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            @Valid @RequestBody RecordPaymentRequest request
    ) {
        var p = service.record(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(PaymentResponse.fromEntity(p)));
    }

    @PutMapping("/api/payments/{id}/mark-paid")
    @Operation(summary = "Flip an UNPAID payment to PAID with a method + paidAt. Idempotent on already-PAID.")
    public ResponseEntity<ApiResponse<PaymentResponse>> markPaid(
            @PathVariable UUID id,
            @Valid @RequestBody MarkPaidRequest request
    ) {
        var p = service.markPaid(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of(PaymentResponse.fromEntity(p)));
    }

    @GetMapping("/api/payments/{id}")
    @Operation(summary = "Get a payment by id. OWNER, assigned INSTRUCTOR, the student, or linked PARENT.")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable UUID id) {
        var p = service.getById(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(p));
    }

    @GetMapping("/api/students/{id}/payments")
    @Operation(summary = "Student's full ledger — payments + summary totals.")
    public ResponseEntity<ApiResponse<StudentLedgerResponse>> getStudentLedger(@PathVariable UUID id) {
        var ledger = service.getStudentLedger(SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(ledger));
    }

    @GetMapping("/api/schools/{id}/payments/outstanding")
    @Operation(summary = "All UNPAID payments at the school. OWNER of school only.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getOutstanding(@PathVariable UUID id) {
        var list = service.getOutstandingForSchool(
                SecurityUtil.currentRole(), SecurityUtil.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.of(list));
    }
}
