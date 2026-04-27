package com.sroadtutor.marketplace.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.marketplace.dto.DealershipLeadResponse;
import com.sroadtutor.marketplace.dto.SubmitMatchmakerRequest;
import com.sroadtutor.marketplace.model.DealershipLead;
import com.sroadtutor.marketplace.repository.DealershipLeadRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.ParentStudentRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchmakerServiceTest {

    @Mock DealershipLeadRepository leadRepo;
    @Mock StudentRepository studentRepo;
    @Mock ParentStudentRepository parentLinkRepo;

    @InjectMocks MatchmakerService service;

    @Test
    void submit_createsLeadForLinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(true);
        when(leadRepo.findFirstByStudentIdAndStatus(studentId, "NEW")).thenReturn(Optional.empty());
        when(leadRepo.save(any(DealershipLead.class))).thenAnswer(inv -> {
            DealershipLead l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });

        var req = new SubmitMatchmakerRequest(
                studentId,
                Map.of("make", "Toyota", "bodyType", "SUV"),
                new BigDecimal("25000.00"),
                Boolean.TRUE);

        DealershipLeadResponse resp = service.submit(Role.PARENT, parentId, req);
        assertThat(resp.studentId()).isEqualTo(studentId);
        assertThat(resp.parentUserId()).isEqualTo(parentId);
        assertThat(resp.status()).isEqualTo("NEW");
        assertThat(resp.budget()).isEqualByComparingTo("25000.00");
        assertThat(resp.financingReady()).isTrue();
        assertThat(resp.vehiclePrefJson()).contains("Toyota").contains("SUV");
    }

    @Test
    void submit_overwritesExistingNewLead() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();
        DealershipLead existing = DealershipLead.builder()
                .id(existingId).studentId(studentId).parentUserId(parentId)
                .status("NEW").vehiclePrefJson("{\"make\":\"Honda\"}")
                .budget(new BigDecimal("20000.00")).build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(true);
        when(leadRepo.findFirstByStudentIdAndStatus(studentId, "NEW")).thenReturn(Optional.of(existing));
        when(leadRepo.save(any(DealershipLead.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new SubmitMatchmakerRequest(
                studentId, Map.of("make", "Toyota"), new BigDecimal("30000"), false);

        DealershipLeadResponse resp = service.submit(Role.PARENT, parentId, req);
        assertThat(resp.id()).isEqualTo(existingId);
        assertThat(resp.budget()).isEqualByComparingTo("30000");
        assertThat(resp.vehiclePrefJson()).contains("Toyota");
    }

    @Test
    void submit_rejectsNonParentRole() {
        var req = new SubmitMatchmakerRequest(UUID.randomUUID(), null, null, null);
        assertThatThrownBy(() -> service.submit(Role.OWNER, UUID.randomUUID(), req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submit_rejectsUnlinkedParent() {
        UUID parentId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder().id(studentId).userId(UUID.randomUUID())
                .schoolId(UUID.randomUUID()).status("ACTIVE").build();

        when(studentRepo.findById(studentId)).thenReturn(Optional.of(student));
        when(parentLinkRepo.existsByParentUserIdAndStudentId(parentId, studentId)).thenReturn(false);

        var req = new SubmitMatchmakerRequest(studentId, null, null, null);
        assertThatThrownBy(() -> service.submit(Role.PARENT, parentId, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submit_404IfStudentMissing() {
        when(studentRepo.findById(any())).thenReturn(Optional.empty());
        var req = new SubmitMatchmakerRequest(UUID.randomUUID(), null, null, null);
        assertThatThrownBy(() -> service.submit(Role.PARENT, UUID.randomUUID(), req))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
