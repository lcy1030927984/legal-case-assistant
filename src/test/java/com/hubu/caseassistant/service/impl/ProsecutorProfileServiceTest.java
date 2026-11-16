package com.hubu.caseassistant.service.impl;

import com.hubu.caseassistant.model.ProsecutorProfile;
import com.hubu.caseassistant.repository.ProsecutorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProsecutorProfileServiceTest {

    @Mock
    private ProsecutorProfileRepository prosecutorRepository;

    @InjectMocks
    private ProsecutorProfileService service;

    @Test
    void shouldCreateNewProfileWhenNotFound() {
        when(prosecutorRepository.findByName("张三"))
                .thenReturn(Optional.empty());
        when(prosecutorRepository.saveAndFlush(any(ProsecutorProfile.class)))
                .thenAnswer(inv -> {
                    ProsecutorProfile p = inv.getArgument(0);
                    p.setProsecutorId(java.util.UUID.randomUUID());
                    return p;
                });

        ProsecutorProfile result = service.findOrCreate("张三");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
        verify(prosecutorRepository).saveAndFlush(any(ProsecutorProfile.class));
    }

    @Test
    void shouldReturnExistingProfileWhenFound() {
        ProsecutorProfile existing = new ProsecutorProfile();
        existing.setProsecutorId(java.util.UUID.randomUUID());
        existing.setName("李四");
        existing.setCaseCount(5);

        when(prosecutorRepository.findByName("李四"))
                .thenReturn(Optional.of(existing));

        ProsecutorProfile result = service.findOrCreate("李四");

        assertThat(result).isNotNull();
        assertThat(result.getCaseCount()).isEqualTo(5);
        verify(prosecutorRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateIssueCounts_shouldIncrementFlawCounts() {
        ProsecutorProfile profile = new ProsecutorProfile();
        profile.setName("测试");

        service.updateIssueCounts(profile, "evidence", "flaw");

        assertThat(profile.getTotalFlawCount()).isEqualTo(1);
        assertThat(profile.getEvidenceFlawCount()).isEqualTo(1);
        assertThat(profile.getTotalErrorCount()).isEqualTo(0);
    }

    @Test
    void updateIssueCounts_shouldIncrementErrorCounts() {
        ProsecutorProfile profile = new ProsecutorProfile();
        profile.setName("测试");

        service.updateIssueCounts(profile, "procedure", "error");

        assertThat(profile.getTotalErrorCount()).isEqualTo(1);
        assertThat(profile.getProcedureErrorCount()).isEqualTo(1);
        assertThat(profile.getTotalFlawCount()).isEqualTo(0);
    }
}
