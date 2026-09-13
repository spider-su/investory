package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class AccountingSourceEvidenceServiceTest {
  private final AccountingSourceRepository repository =
      org.mockito.Mockito.mock(AccountingSourceRepository.class);
  private final AccountingSourceEvidenceService service =
      new AccountingSourceEvidenceService(repository);

  @Test
  void uploadIdentityIsDeterministicAndPayloadIsSavedThroughBoundary() {
    byte[] payload = "invoice".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    org.mockito.Mockito.when(
            repository.save(
                org.mockito.ArgumentMatchers.eq(AccountingSourceType.UPLOAD),
                    org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("invoice.pdf"),
                    org.mockito.ArgumentMatchers.eq("application/pdf"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(payload)))
        .thenReturn(7L);

    assertThat(service.receiveUpload("invoice.pdf", "application/pdf", payload)).isEqualTo(7L);
    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(repository)
        .save(
            org.mockito.ArgumentMatchers.eq(AccountingSourceType.UPLOAD),
            captor.capture(),
            org.mockito.ArgumentMatchers.eq("invoice.pdf"),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.argThat(hash -> hash.length == 32),
            org.mockito.ArgumentMatchers.same(payload));
    assertThat(captor.getValue()).startsWith("sha256:").hasSize(71);
  }

  @Test
  void repeatedUploadUsesTheSameDeterministicIdentity() {
    byte[] payload = "same".getBytes();
    org.mockito.Mockito.when(
            repository.save(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(payload)))
        .thenReturn(9L);

    service.receiveUpload("a.pdf", "application/pdf", payload);
    service.receiveUpload("different-name.pdf", "application/pdf", payload);

    org.mockito.ArgumentCaptor<String> identities =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(repository, org.mockito.Mockito.times(2))
        .save(
            org.mockito.ArgumentMatchers.eq(AccountingSourceType.UPLOAD),
            identities.capture(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.same(payload));
    assertThat(identities.getAllValues()).containsOnly(identities.getAllValues().getFirst());
  }

  @Test
  void failedEvidenceCanBeReopenedWithoutDeletingTheOriginal() {
    when(repository.status(7L)).thenReturn(AccountingSourceStatus.FAILED);

    service.retry(7L);

    verify(repository).updateStatus(7L, AccountingSourceStatus.RECEIVED, null);
  }
}
