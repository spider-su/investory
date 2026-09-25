package com.smartbox.investory.integrations.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvBankTransactionSourceTest {
  private static final String HEADER =
      "booking_date;related_period;reference;counterparty;currency;amount;note\n";

  @Test
  void mapsMultipleCurrenciesAndOptionalFields() {
    var page =
        source(
                HEADER
                    + row("2026-09-01", "ZUS 09/2026", "ZUS", "PLN", "-100.00", "paid")
                    + row("2026-09-02", "EUR RECEIPT", "", "eur", "123.45", ""))
            .transactions(query());

    assertThat(page.nextContinuationToken()).isNull();
    assertThat(page.transactions()).hasSize(2);
    assertThat(page.transactions().get(0))
        .extracting(
            ExternalBankTransaction::provider,
            ExternalBankTransaction::externalAccountId,
            ExternalBankTransaction::currency,
            ExternalBankTransaction::amount,
            ExternalBankTransaction::remittanceInformation)
        .containsExactly(
            BankDataProvider.CSV, "JDG_MAIN_ACCOUNT", "PLN", new BigDecimal("-100.00"), "paid");
    assertThat(page.transactions().get(1).counterpartyName()).isEmpty();
  }

  @Test
  void derivesStableIdentityIndependentOfRowPosition() {
    var first =
        source(HEADER + row("2026-09-01", "REF", "BANK", "PLN", "-1", "note"))
            .transactions(query())
            .transactions()
            .get(0);
    var second =
        source(
                HEADER
                    + row("2026-09-02", "OTHER", "BANK", "PLN", "-2", "other")
                    + row("2026-09-01", "REF", "BANK", "PLN", "-1", "note"))
            .transactions(query())
            .transactions()
            .stream()
            .filter(transaction -> transaction.rawReference().equals("REF"))
            .findFirst()
            .orElseThrow();

    assertThat(first.externalTransactionId()).isEqualTo(second.externalTransactionId());
  }

  @Test
  void rejectsMalformedRows() {
    assertThatThrownBy(() -> source(HEADER + "2026-09-01;only-two-columns").transactions(query()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("7 columns");
  }

  @Test
  void acceptsUtf8BomQuotedFieldsAndWindowsLineEndings() {
    var page =
        new CsvBankTransactionSource(
                ("\uFEFF\"booking_date\";\"related_period\";\"reference\";\"counterparty\";\"currency\";\"amount\";\"note\"\r\n"
                        + "2026-09-01;;REF;\"ACME; Sp. z o.o.\";PLN;-10,50;\"paid\"\r\n")
                    .getBytes(StandardCharsets.UTF_8),
                "JDG_MAIN_ACCOUNT")
            .transactions(query());

    assertThat(page.transactions()).hasSize(1);
    assertThat(page.transactions().getFirst())
        .extracting(
            ExternalBankTransaction::counterpartyName,
            ExternalBankTransaction::amount,
            ExternalBankTransaction::remittanceInformation)
        .containsExactly("ACME; Sp. z o.o.", new BigDecimal("-10.50"), "paid");
  }

  @Test
  void mapsPekaoEurExportAndDetectsTheOwnAccount() {
    var page =
        source(
                "Data księgowania;Data waluty;Nadawca / Odbiorca;Adres nadawcy / odbiorcy;Rachunek źródłowy;Rachunek docelowy;Tytułem;Kwota operacji;Waluta;Numer referencyjny;Typ operacji\n"
                    + "04.09.2026;04.09.2026;Example Services Ltd;;'SACC;'TEST_BANK_ACCOUNT_001;Service Agreement;7 661,00;EUR;'RI5;SEPA CREDIT TRANSFER INCOMING\n"
                    + "04.09.2026;04.09.2026;Example Account Holder;;'TEST_BANK_ACCOUNT_001;'TEST_COUNTERPARTY_001;Transfer of funds;-7 661,00;EUR;'163;MOBILE TRANSFER\n")
            .transactions(query());

    assertThat(page.transactions()).hasSize(2);
    assertThat(page.transactions().getFirst())
        .extracting(
            ExternalBankTransaction::externalAccountId,
            ExternalBankTransaction::counterpartyAccount,
            ExternalBankTransaction::rawReference,
            ExternalBankTransaction::currency,
            ExternalBankTransaction::amount)
        .containsExactly("TEST_BANK_ACCOUNT_001", "SACC", "RI5", "EUR", new BigDecimal("7661.00"));
    assertThat(page.transactions().get(1).counterpartyAccount()).isEqualTo("TEST_COUNTERPARTY_001");
  }

  private CsvBankTransactionSource source(String csv) {
    return new CsvBankTransactionSource(csv.getBytes(), "JDG_MAIN_ACCOUNT");
  }

  private BankTransactionQuery query() {
    return new BankTransactionQuery("JDG_MAIN_ACCOUNT", null, null, null);
  }

  private String row(
      String date,
      String reference,
      String counterparty,
      String currency,
      String amount,
      String note) {
    return String.join(";", date, "2026-09-01", reference, counterparty, currency, amount, note)
        + "\n";
  }
}
