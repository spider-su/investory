package com.example.demo.services.notifications;

import com.example.demo.data.ImportBatchStatus;
import com.example.demo.data.repository.ImportBatch;
import com.example.demo.data.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Fires when no successful import batch has been recorded for longer than the configured threshold.
 */
@Component
@RequiredArgsConstructor
public class StaleImportAlertRule implements AlertRule {

    private final ImportBatchRepository importBatchRepository;
    private final NotificationProperties properties;

    @Override
    public String code() {
        return "STALE_IMPORT";
    }

    @Override
    public Optional<String> evaluate() {
        Optional<ImportBatch> latest = importBatchRepository.findFirstByOrderByIdDesc();
        if (latest.isEmpty()) {
            return Optional.of("No broker imports recorded yet.");
        }
        ImportBatch batch = latest.get();
        ZonedDateTime ts = batch.getFinishedAt() != null ? batch.getFinishedAt() : batch.getStartedAt();
        if (ts == null) {
            return Optional.empty();
        }
        long ageDays = ChronoUnit.DAYS.between(ts.toLocalDate(), ZonedDateTime.now().toLocalDate());
        int threshold = properties.getStaleImportDays();
        if (ageDays >= threshold || batch.getStatus() == ImportBatchStatus.FAILED) {
            return Optional.of(String.format(
                    "Stale import: last batch #%d (%s, %s) is %d day(s) old.",
                    batch.getId(), batch.getBroker(), batch.getStatus(), ageDays));
        }
        return Optional.empty();
    }
}

