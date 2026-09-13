package com.smartbox.investory.accounting.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer;
import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountingStagingAcquisitionServiceTest {
  private final AccountingStagingRepository repository = mock(AccountingStagingRepository.class);
  private final AccountingStagingAcquisitionService service =
      new AccountingStagingAcquisitionService(repository, new AccountingExpenseNormalizer());

  @Test
  void storesExplicitValidVatTreatment() {
    when(repository.insertInvoice(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(42L);

    assertThat(service.stageInvoice(1, salesInvoice(), "DOMESTIC_VAT")).isEqualTo(42L);
    Object[] arguments =
        org.mockito.Mockito.mockingDetails(repository)
            .getInvocations()
            .iterator()
            .next()
            .getArguments();
    assertThat(arguments[17]).isEqualTo("DOMESTIC_VAT");
  }

  @Test
  void rejectsUnknownDirectionAndInvalidVatTreatmentBeforeStaging() {
    assertThatThrownBy(() -> service.stageInvoice(1, invoice("UNKNOWN"), "DOMESTIC_VAT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Choose sales, purchase, receipt, or correction");
    assertThatThrownBy(() -> service.stageInvoice(1, salesInvoice(), "NOT_A_TREATMENT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported VAT treatment");
    assertThatThrownBy(() -> service.stageInvoice(1, salesInvoice(), "DOMESTIC_PURCHASE"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not valid");
  }

  private ReviewedInvoice salesInvoice() {
    return invoice("SALES_INVOICE");
  }

  private ReviewedInvoice invoice(String documentType) {
    return new ReviewedInvoice(
        LocalDate.of(2026, 3, 1),
        documentType,
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 3, 1),
        "FV-1",
        "Customer",
        "CONSULTING",
        "PLN",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        new BigDecimal("123.00"),
        BigDecimal.ONE,
        "REVIEWED",
        null,
        "10");
  }
}
