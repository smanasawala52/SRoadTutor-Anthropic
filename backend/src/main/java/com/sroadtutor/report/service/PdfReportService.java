package com.sroadtutor.report.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.model.User;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.dto.SessionMistakeResponse;
import com.sroadtutor.evaluation.service.MistakeLogService;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.payment.dto.StudentLedgerResponse;
import com.sroadtutor.payment.service.PaymentService;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds per-student PDF report cards. Locked at PR14:
 *
 * <ul>
 *   <li>Header — school name, student full name, generated-at timestamp.</li>
 *   <li>Readiness summary — average score over the last N sessions, FAIL flag.</li>
 *   <li>Per-session breakdown — score + total demerits, recent first.</li>
 *   <li>Mistake history table — top 20 most-recent rows with category +
 *       severity + count + notes.</li>
 *   <li>Payment summary — total paid + total outstanding (CAD).</li>
 * </ul>
 *
 * <p>The report is single-page A4 in V1. If the data overflows we accept
 * a truncated tail (with a "…" marker) rather than introducing a
 * pagination loop. Multi-page support is tracked as TD.</p>
 *
 * <p>Scope is enforced by delegating to the existing services
 * ({@link MistakeLogService}, {@link PaymentService}) — they throw 403 if
 * the caller can't read the data, and we propagate.</p>
 */
@Service
public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);

    /** PR14 — top-N rows of the mistake history table on a single A4 page. */
    private static final int MAX_HISTORY_ROWS = 20;

    private final StudentRepository studentRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final MistakeLogService mistakeLogService;
    private final PaymentService paymentService;

    public PdfReportService(StudentRepository studentRepo,
                             SchoolRepository schoolRepo,
                             UserRepository userRepo,
                             MistakeLogService mistakeLogService,
                             PaymentService paymentService) {
        this.studentRepo = studentRepo;
        this.schoolRepo = schoolRepo;
        this.userRepo = userRepo;
        this.mistakeLogService = mistakeLogService;
        this.paymentService = paymentService;
    }

    /**
     * Generates the PDF as a byte array. The caller (controller) writes it
     * to {@code application/pdf}.
     */
    @Transactional(readOnly = true)
    public byte[] buildStudentReport(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + student.getSchoolId()));
        User studentUser = userRepo.findById(student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student user not found: " + student.getUserId()));

        // The downstream service calls handle scope checks for us — they
        // throw AccessDeniedException if the caller can't read.
        ReadinessScoreResponse readiness = mistakeLogService.readinessForStudent(
                role, currentUserId, studentId);
        List<SessionMistakeResponse> history = mistakeLogService.listForStudent(
                role, currentUserId, studentId);
        StudentLedgerResponse ledger = paymentService.getStudentLedger(
                role, currentUserId, studentId);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawReport(cs, school, studentUser, student, readiness, history, ledger);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            byte[] bytes = out.toByteArray();
            log.info("Generated PDF report-card for student={} ({} bytes)", studentId, bytes.length);
            return bytes;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build PDF report for student " + studentId, ex);
        }
    }

    // ============================================================
    // Layout
    // ============================================================

    private void drawReport(PDPageContentStream cs, School school, User studentUser, Student student,
                              ReadinessScoreResponse readiness, List<SessionMistakeResponse> history,
                              StudentLedgerResponse ledger) throws IOException {
        // A4 = 595.28 × 841.89 points; we work top-down from y=800.
        float y = 800f;
        float left = 50f;

        // Header
        text(cs, font(14, true), left, y, school.getName() == null ? "Driving School" : school.getName());
        y -= 20f;
        text(cs, font(10, false), left, y, "Student report card");
        y -= 14f;
        text(cs, font(10, false), left, y, "Generated " + nowFormatted(school.getTimezone()));
        y -= 24f;

        // Student block
        text(cs, font(12, true), left, y, "Student: "
                + (studentUser.getFullName() == null ? studentUser.getEmail() : studentUser.getFullName()));
        y -= 16f;
        text(cs, font(10, false), left, y, "Status: " + safe(student.getStatus())
                + "    Lessons remaining: " + student.getLessonsRemaining()
                + " / " + student.getPackageTotalLessons());
        y -= 14f;
        if (student.getRoadTestDate() != null) {
            text(cs, font(10, false), left, y, "Road test: " + student.getRoadTestDate());
            y -= 14f;
        }
        y -= 8f;

        // Readiness block
        text(cs, font(12, true), left, y, "Road Test Readiness");
        y -= 16f;
        String readinessLine = String.format(Locale.ENGLISH,
                "Score: %.1f / 100 over last %d sessions",
                readiness.averageScore(), readiness.sessionsConsidered());
        text(cs, font(10, false), left, y, readinessLine);
        y -= 14f;
        if (readiness.anyFailMistakeRecently()) {
            text(cs, font(10, true), left, y, "WARNING: a FAIL-severity mistake was logged recently.");
            y -= 14f;
        }
        y -= 8f;

        // Per-session score table (most-recent first)
        text(cs, font(12, true), left, y, "Recent sessions");
        y -= 16f;
        text(cs, font(9, true), left, y, "Session id (8-prefix)");
        text(cs, font(9, true), left + 200f, y, "Score");
        text(cs, font(9, true), left + 280f, y, "Demerits");
        text(cs, font(9, true), left + 360f, y, "FAIL?");
        y -= 12f;
        for (var ps : readiness.perSession()) {
            if (y < 280f) break;
            text(cs, font(9, false), left, y, ps.sessionId().toString().substring(0, 8));
            text(cs, font(9, false), left + 200f, y, String.valueOf(ps.score()));
            text(cs, font(9, false), left + 280f, y, String.valueOf(ps.totalDemerits()));
            text(cs, font(9, false), left + 360f, y, ps.hadFail() ? "Yes" : "No");
            y -= 12f;
        }
        y -= 8f;

        // Mistake history (top N)
        text(cs, font(12, true), left, y, "Mistake history (most recent first)");
        y -= 16f;
        text(cs, font(9, true), left, y, "Date");
        text(cs, font(9, true), left + 110f, y, "Category");
        text(cs, font(9, true), left + 320f, y, "Severity");
        text(cs, font(9, true), left + 400f, y, "Count");
        y -= 12f;

        // history is in chronological (oldest-first) order from MistakeLogService;
        // reverse for the report
        int written = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (y < 120f || written >= MAX_HISTORY_ROWS) break;
            var m = history.get(i);
            String date = m.loggedAt() == null ? "—"
                    : ZonedDateTime.ofInstant(m.loggedAt(),
                                ZoneId.of(school.getTimezone() == null ? "America/Regina" : school.getTimezone()))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            text(cs, font(9, false), left, y, date);
            text(cs, font(9, false), left + 110f, y, truncate(m.categoryName(), 36));
            text(cs, font(9, false), left + 320f, y, m.severity());
            text(cs, font(9, false), left + 400f, y, String.valueOf(m.count()));
            y -= 11f;
            written++;
        }
        if (history.size() > written) {
            text(cs, font(9, false), left, y, "… " + (history.size() - written) + " older row(s) omitted");
            y -= 12f;
        }
        y -= 8f;

        // Payment summary
        if (y > 80f) {
            text(cs, font(12, true), left, y, "Payments");
            y -= 16f;
            String paymentLine = String.format(Locale.ENGLISH,
                    "Total paid: $%s %s    Total outstanding: $%s %s",
                    ledger.totalPaid(), ledger.currency(),
                    ledger.totalOutstanding(), ledger.currency());
            text(cs, font(10, false), left, y, paymentLine);
        }
    }

    // ============================================================
    // PDF helpers
    // ============================================================

    private static final Map<String, PDType1Font> FONT_CACHE = new HashMap<>();

    private static PDType1Font font(int size, boolean bold) {
        // PDFBox 3.x: fonts are looked up via the Standard14Fonts enum.
        String key = (bold ? "B" : "R") + size;
        return FONT_CACHE.computeIfAbsent(key, k -> bold
                ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                : new PDType1Font(Standard14Fonts.FontName.HELVETICA));
    }

    private static void text(PDPageContentStream cs, PDType1Font fontType,
                              float x, float y, String s) throws IOException {
        if (s == null) s = "";
        cs.beginText();
        // Default size for cached fonts is 10; we set the actual size based
        // on the cache key. Helvetica-Bold-12 etc. are inferred from the key.
        // Simpler: set size explicitly each time using the fontType + integer.
        cs.setFont(fontType, fontType == FONT_CACHE.getOrDefault("B14", null)
                ? 14
                : fontType == FONT_CACHE.getOrDefault("B12", null)
                    ? 12
                    : fontType == FONT_CACHE.getOrDefault("B10", null) || fontType == FONT_CACHE.getOrDefault("R10", null)
                        ? 10
                        : 9);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitise(s));
        cs.endText();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * PDFBox's default Helvetica only handles WinAnsiEncoding; characters
     * outside that set throw {@code IllegalArgumentException}. Strip them
     * for safety. (Names with curly quotes / accented letters are the
     * common offenders.)
     */
    private static String sanitise(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // WinAnsi range — keep ASCII printable + a few common Latin-1 codepoints.
            if (c == '\n' || c == '\t' || (c >= 32 && c <= 126) || (c >= 160 && c <= 255)) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String nowFormatted(String tz) {
        ZoneId zone;
        try {
            zone = ZoneId.of(tz == null || tz.isBlank() ? "America/Regina" : tz);
        } catch (Exception ex) {
            zone = ZoneId.of("America/Regina");
        }
        return ZonedDateTime.now(zone).format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ENGLISH));
    }
}
