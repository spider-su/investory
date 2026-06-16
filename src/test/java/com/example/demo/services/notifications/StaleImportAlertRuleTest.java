package com.example.demo.services.notifications;

import com.example.demo.data.BrokerType;
import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleImportAlertRuleTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    private NotificationProperties properties;
    private StaleImportAlertRule rule;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setStaleImportDays(7);
        rule = new StaleImportAlertRule(importBatchRepository, properties);
    }

    @Test
    void evaluate_firesWhenNoBatchesExist() {
        when(importBatchRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("No broker imports"));
    }

    @Test
    void evaluate_firesWhenLastBatchIsOlderThanThreshold() {
        when(importBatchRepository.findFirstByOrderByIdDesc())
                .thenReturn(Optional.of(batch(ImportBatchStatus.APPLIED, ZonedDateTime.now().minusDays(30))));

        Optional<String> result = rule.evaluate();

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Stale import"));
    }

    @Test
    void evaluate_firesWhenLastBatchFailed() {
        when(importBatchRepository.findFirstByOrderByIdDesc())
                .thenReturn(Optional.of(batch(ImportBatchStatus.FAILED, ZonedDateTime.now())));

        assertTrue(rule.evaluate().isPresent());
    }

    @Test
    void evaluate_isQuietForFreshAppliedBatch() {
        when(importBatchRepository.findFirstByOrderByIdDesc())
                .thenReturn(Optional.of(batch(ImportBatchStatus.APPLIED, ZonedDateTime.now())));

        assertFalse(rule.evaluate().isPresent());
    }

    private static ImportBatch batch(ImportBatchStatus status, ZonedDateTime ts) {
        ImportBatch b = new ImportBatch();
        b.setId(1L);
        b.setBroker(BrokerType.XTB);
        b.setStatus(status);
        b.setStartedAt(ts);
        b.setFinishedAt(ts);
        return b;
    }
}

