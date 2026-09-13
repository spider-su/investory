package com.smartbox.investory.accounting.staging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer;
import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    assertThat(arguments[18]).isEqualTo("DOMESTIC_VAT");
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

  @Test
  void bankFileRoutesRowsByBookingMonthAndKeepsProviderIdentity() {
    var sources = mock(com.smartbox.investory.accounting.AccountingSourceEvidenceService.class);
    var bank = new AccountingBankStagingImportService(sources, service, "JDG_MAIN_ACCOUNT");
    when(sources.receiveBank(any(), any(), any(), any())).thenReturn(7L);

    bank.stageFile(
        1,
        "statement.csv",
        "text/csv",
        ("booking_date,related_period,reference,counterparty,currency,amount,note\n"
                + "2026-01-31,,JAN,CUSTOMER,PLN,10.00,received\n"
                + "2026-02-02,,FEB,CUSTOMER,PLN,20.00,received\n")
            .getBytes(StandardCharsets.UTF_8),
        LocalDate.of(2026, 2, 1));

    // The method is called once per row; inspect the captured invocation arguments directly.
    var invocations =
        org.mockito.Mockito.mockingDetails(repository).getInvocations().stream().toList();
    assertThat(invocations).hasSize(2);
    assertThat(invocations.get(0).getArguments()[1]).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(invocations.get(1).getArguments()[1]).isEqualTo(LocalDate.of(2026, 2, 1));
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
