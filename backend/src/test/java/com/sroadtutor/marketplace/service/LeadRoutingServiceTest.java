package com.sroadtutor.marketplace.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.marketplace.model.Dealership;
import com.sroadtutor.marketplace.model.DealershipLead;
import com.sroadtutor.marketplace.model.InstructorPayout;
import com.sroadtutor.marketplace.repository.DealershipLeadRepository;
import com.sroadtutor.marketplace.repository.DealershipRepository;
import com.sroadtutor.marketplace.repository.InstructorPayoutRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadRoutingServiceTest {

    @Mock DealershipLeadRepository leadRepo;
    @Mock DealershipRepository dealershipRepo;
    @Mock InstructorPayoutRepository payoutRepo;
    @Mock StudentRepository studentRepo;
    @Mock SchoolRepository schoolRepo;

    @InjectMocks LeadRoutingService service;

    // ============================================================
    // Graduation trigger
    // ============================================================

    @Test
    void onStudentPassed_routesLeadToProvinceDealership() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID dealerId = UUID.randomUUID();

        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("NEW").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).province("SK").build();
        Dealership picked = Dealership.builder()
                .id(dealerId).province("SK").active(true)
                .name("ABC Motors").bountyPerLead(new BigDecimal("250.00")).build();

        when(leadRepo.findFirstByStudentIdAndStatus(studentId, "NEW")).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(dealershipRepo.findByActiveTrueAndProvince("SK")).thenReturn(List.of(picked));
        when(leadRepo.save(any(DealershipLead.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<DealershipLead> result = service.onStudentPassed(studentId);
        assertThat(result).isPresent();
        assertThat(lead.getStatus()).isEqualTo("ROUTED");
        assertThat(lead.getDealershipId()).isEqualTo(dealerId);
        assertThat(lead.getBountyAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void onStudentPassed_noOpWhenNoLead() {
        UUID studentId = UUID.randomUUID();
        when(leadRepo.findFirstByStudentIdAndStatus(studentId, "NEW")).thenReturn(Optional.empty());

        Optional<DealershipLead> result = service.onStudentPassed(studentId);
        assertThat(result).isEmpty();
        verify(leadRepo, never()).save(any());
    }

    @Test
    void onStudentPassed_skipsRoutingWhenNoDealershipInProvince() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("NEW").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).province("SK").build();

        when(leadRepo.findFirstByStudentIdAndStatus(studentId, "NEW")).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(dealershipRepo.findByActiveTrueAndProvince("SK")).thenReturn(List.of());

        Optional<DealershipLead> result = service.onStudentPassed(studentId);
        assertThat(result).isEmpty();
        // Lead stays NEW
        assertThat(lead.getStatus()).isEqualTo("NEW");
    }

    // ============================================================
    // Conversion + payout
    // ============================================================

    @Test
    void markConverted_setsStatusAndAutoCreatesPayout() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED")
                .dealershipId(UUID.randomUUID()).bountyAmount(new BigDecimal("250")).build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).instructorId(instructorId).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(payoutRepo.findByLeadId(leadId)).thenReturn(Optional.empty());
        when(leadRepo.save(any(DealershipLead.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutRepo.save(any(InstructorPayout.class))).thenAnswer(inv -> inv.getArgument(0));

        DealershipLead after = service.markConverted(Role.OWNER, ownerId, leadId);
        assertThat(after.getStatus()).isEqualTo("CONVERTED");
        assertThat(after.getConvertedAt()).isNotNull();

        ArgumentCaptor<InstructorPayout> cap = ArgumentCaptor.forClass(InstructorPayout.class);
        verify(payoutRepo).save(cap.capture());
        assertThat(cap.getValue().getInstructorId()).isEqualTo(instructorId);
        assertThat(cap.getValue().getLeadId()).isEqualTo(leadId);
        assertThat(cap.getValue().getPayoutAmount()).isEqualByComparingTo("100.00");
        assertThat(cap.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void markConverted_idempotentOnAlreadyConverted() {
        UUID leadId = UUID.randomUUID();
        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(UUID.randomUUID()).status("CONVERTED").build();
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));

        DealershipLead after = service.markConverted(Role.OWNER, UUID.randomUUID(), leadId);
        assertThat(after).isSameAs(lead);
        verify(payoutRepo, never()).save(any());
    }

    @Test
    void markConverted_rejectsNewLead() {
        UUID leadId = UUID.randomUUID();
        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(UUID.randomUUID()).status("NEW").build();
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.markConverted(Role.OWNER, UUID.randomUUID(), leadId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("LEAD_NOT_ROUTED"));
    }

    @Test
    void markConverted_403ForUnrelatedOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.markConverted(Role.OWNER, UUID.randomUUID(), leadId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void markConverted_skipsPayoutWhenNoInstructor() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).instructorId(null).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(leadRepo.save(any(DealershipLead.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markConverted(Role.OWNER, ownerId, leadId);
        verify(payoutRepo, never()).save(any());
    }

    @Test
    void markPayoutPaid_flipsStatusAndRecordsRef() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();

        InstructorPayout payout = InstructorPayout.builder()
                .id(payoutId).leadId(leadId).instructorId(UUID.randomUUID())
                .payoutAmount(new BigDecimal("100.00")).status("PENDING").build();
        DealershipLead lead = DealershipLead.builder()
                .id(leadId).studentId(studentId).status("CONVERTED").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(payoutRepo.findById(payoutId)).thenReturn(Optional.of(payout));
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(payoutRepo.save(any(InstructorPayout.class))).thenAnswer(inv -> inv.getArgument(0));

        InstructorPayout after = service.markPayoutPaid(Role.OWNER, ownerId, payoutId, "ETR-12345");
        assertThat(after.getStatus()).isEqualTo("PAID");
        assertThat(after.getETransferRef()).isEqualTo("ETR-12345");
        assertThat(after.getPaidAt()).isNotNull();
    }

    @Test
    void markPayoutPaid_idempotentOnAlreadyPaid() {
        UUID payoutId = UUID.randomUUID();
        InstructorPayout payout = InstructorPayout.builder()
                .id(payoutId).leadId(UUID.randomUUID()).instructorId(UUID.randomUUID())
                .payoutAmount(new BigDecimal("100")).status("PAID").build();
        when(payoutRepo.findById(payoutId)).thenReturn(Optional.of(payout));

        InstructorPayout after = service.markPayoutPaid(Role.OWNER, UUID.randomUUID(), payoutId, "X");
        assertThat(after).isSameAs(payout);
        verify(payoutRepo, never()).save(any());
    }
}
