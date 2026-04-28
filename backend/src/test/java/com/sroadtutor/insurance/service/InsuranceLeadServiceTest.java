package com.sroadtutor.insurance.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.insurance.model.InsuranceBroker;
import com.sroadtutor.insurance.model.InsuranceLead;
import com.sroadtutor.insurance.repository.InsuranceBrokerRepository;
import com.sroadtutor.insurance.repository.InsuranceLeadRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class InsuranceLeadServiceTest {

    @Mock InsuranceLeadRepository leadRepo;
    @Mock InsuranceBrokerRepository brokerRepo;
    @Mock StudentRepository studentRepo;
    @Mock SchoolRepository schoolRepo;

    @InjectMocks InsuranceLeadService service;

    @Test
    void onStudentPassed_routesToProvinceBroker() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();

        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).province("SK").build();
        InsuranceBroker broker = InsuranceBroker.builder()
                .id(brokerId).province("SK").active(true)
                .name("Sask Insurance").bountyPerQuote(new BigDecimal("75.00")).build();

        when(leadRepo.findFirstByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(Optional.empty());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(brokerRepo.findEligibleForProvince("SK")).thenReturn(List.of(broker));
        when(leadRepo.save(any(InsuranceLead.class))).thenAnswer(inv -> {
            InsuranceLead l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });

        Optional<InsuranceLead> result = service.onStudentPassed(studentId);
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("ROUTED");
        assertThat(result.get().getBrokerId()).isEqualTo(brokerId);
        assertThat(result.get().getBountyAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void onStudentPassed_createsNewWhenNoBroker() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).province("SK").build();

        when(leadRepo.findFirstByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(Optional.empty());
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(brokerRepo.findEligibleForProvince("SK")).thenReturn(List.of());
        when(leadRepo.save(any(InsuranceLead.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<InsuranceLead> result = service.onStudentPassed(studentId);
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("NEW");
        assertThat(result.get().getBrokerId()).isNull();
    }

    @Test
    void onStudentPassed_skipsWhenActiveLeadExists() {
        UUID studentId = UUID.randomUUID();
        InsuranceLead existing = InsuranceLead.builder()
                .id(UUID.randomUUID()).studentId(studentId).status("ROUTED").build();
        when(leadRepo.findFirstByStudentIdOrderByCreatedAtDesc(studentId)).thenReturn(Optional.of(existing));

        Optional<InsuranceLead> result = service.onStudentPassed(studentId);
        assertThat(result).isEmpty();
        verify(leadRepo, never()).save(any());
    }

    @Test
    void markQuoted_setsTimestampAndStatus() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        InsuranceLead lead = InsuranceLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED")
                .brokerId(UUID.randomUUID()).bountyAmount(new BigDecimal("75")).build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));
        when(leadRepo.save(any(InsuranceLead.class))).thenAnswer(inv -> inv.getArgument(0));

        InsuranceLead after = service.markQuoted(Role.OWNER, ownerId, leadId);
        assertThat(after.getStatus()).isEqualTo("QUOTED");
        assertThat(after.getQuotedAt()).isNotNull();
    }

    @Test
    void markQuoted_rejectsNewLead() {
        UUID leadId = UUID.randomUUID();
        InsuranceLead lead = InsuranceLead.builder()
                .id(leadId).studentId(UUID.randomUUID()).status("NEW").build();
        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.markQuoted(Role.OWNER, UUID.randomUUID(), leadId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSURANCE_LEAD_NOT_ROUTED"));
    }

    @Test
    void markConverted_rejectsRoutedLead() {
        UUID ownerId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        InsuranceLead lead = InsuranceLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(ownerId).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.markConverted(Role.OWNER, ownerId, leadId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getCode())
                        .isEqualTo("INSURANCE_LEAD_NOT_QUOTED"));
    }

    @Test
    void quote_403ForUnrelatedOwner() {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        InsuranceLead lead = InsuranceLead.builder()
                .id(leadId).studentId(studentId).status("ROUTED").build();
        Student student = Student.builder().id(studentId).schoolId(schoolId)
                .userId(UUID.randomUUID()).status("PASSED").build();
        School school = School.builder().id(schoolId).ownerId(UUID.randomUUID()).build();

        when(leadRepo.findById(leadId)).thenReturn(Optional.of(lead));
        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(schoolRepo.findById(schoolId)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.markQuoted(Role.OWNER, UUID.randomUUID(), leadId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
