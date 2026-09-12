package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvoiceTextParserTest {
  private final InvoiceTextParser parser = new InvoiceTextParser();

  @Test
  void parsesSanitizedPolishVatInvoiceText() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Faktura VAT: FV 123/2026
            Data wystawienia: 12.03.2026
            Data sprzedaży: 11.03.2026
            Sprzedawca: Demo Services Sp. z o.o. NIP: 123-456-78-90
            Nabywca: Demo Buyer Sp. z o.o. NIP: 987-654-32-10
            Netto: 1 000,00 PLN
            VAT 23%: 230,00 PLN
            Brutto: 1 230,00 PLN
            """);

    assertThat(result.status()).as(result.warnings().toString()).isEqualTo(ScanStatus.COMPLETE);
    assertThat(result.invoice().reference()).isEqualTo("FV 123/2026");
    assertThat(result.invoice().issueDate().toString()).isEqualTo("2026-03-12");
    assertThat(result.invoice().saleDate().toString()).isEqualTo("2026-03-11");
    assertThat(result.invoice().seller()).contains("Demo Services");
    assertThat(result.invoice().buyer()).contains("Demo Buyer");
    assertThat(result.invoice().sellerNip()).isEqualTo("1234567890");
    assertThat(result.invoice().buyerNip()).isEqualTo("9876543210");
    assertThat(result.invoice().netAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.invoice().vatAmount()).isEqualByComparingTo(new BigDecimal("230.00"));
    assertThat(result.invoice().grossAmount()).isEqualByComparingTo(new BigDecimal("1230.00"));
  }

  @Test
  void marksTextWithMissingAccountingTotalsAsPartial() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Faktura VAT: BP 123/2026
            Data wystawienia: 12.03.2026
            Sprzedawca: Demo Fuel Sp. z o.o. NIP: 1234567890
            Netto: 100,00 PLN
            """);

    assertThat(result.status()).isEqualTo(ScanStatus.PARTIAL);
    assertThat(result.invoice()).isNull();
    assertThat(result.warnings()).contains("missing VAT amount", "missing gross amount");
  }

  @Test
  void parsesSanitizedRealWorldTableTotals() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Faktura 1/10/2021
            Data sprzedaży 2021-10-29
            Data wystawienia 2021-10-29
            Termin zapłaty 2021-11-12
            Sprzedawca
            Nazwa DEMO CONSULTING
            NIP 8133703437
            Nabywca
            Nazwa DEMO PROFESSIONAL SERVICES sp. z o.o.
            NIP 5213635475
            Wartość brutto PKWiU Nazwa Kwota VAT Stawka VAT Wartość netto
            1 Usługi podstawowe 36 801,60 6 881,60 23% 29 920,00 szt.
            2 Usługi dodatkowe 461,25 86,25 23% 375,00 szt.
            37 262,85 6 967,85 30 295,00 Razem:
            Razem do zapłaty: 37 262,85 PLN
            """);

    assertThat(result.status()).as(result.warnings().toString()).isEqualTo(ScanStatus.COMPLETE);
    assertThat(result.invoice().netAmount()).isEqualByComparingTo("30295.00");
    assertThat(result.invoice().vatAmount()).isEqualByComparingTo("6967.85");
    assertThat(result.invoice().grossAmount()).isEqualByComparingTo("37262.85");
  }

  @Test
  void parsesReverseChargeTotalWithoutVatAmount() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Invoice 1/3/2023
            Issue date 2023-03-31
            Seller: DEMO SELLER VAT ID 8133703437
            Buyer: DEMO BUYER
            1 IT service 4,000.00 0.00 np 4,000.00
            4,000.00 0.00 4,000.00 Total:
            Total: 4,000.00 USD
            Remarks: reverse charge
            """);

    assertThat(result.status()).as(result.warnings().toString()).isEqualTo(ScanStatus.COMPLETE);
    assertThat(result.invoice().currency()).isEqualTo("USD");
    assertThat(result.invoice().netAmount()).isEqualByComparingTo("4000.00");
    assertThat(result.invoice().vatAmount()).isEqualByComparingTo("0.00");
    assertThat(result.invoice().grossAmount()).isEqualByComparingTo("4000.00");
  }

  @Test
  void parsesVatExemptAndCorrectionTotals() {
    InvoiceTextParser.ParseResult benefits =
        parser.parse(
            """
            Faktura 1324/1766/09/21
            Data wystawienia 2021-09-28
            Sprzedawca: DEMO BENEFITS NIP 5213635475
            Nabywca: DEMO BUYER NIP 8133703437
            Netto VAT Brutto
            295,00 ZW 0,00 295,00
            Razem do zapłaty: 295,00 PLN
            """);
    assertThat(benefits.status()).as(benefits.warnings().toString()).isEqualTo(ScanStatus.COMPLETE);
    assertThat(benefits.invoice().vatAmount()).isZero();

    InvoiceTextParser.ParseResult correction =
        parser.parse(
            """
            Faktura korygująca
            KOR/1/9/2022
            Data wystawienia 2022-09-30
            Sprzedawca: DEMO SELLER NIP 8133703437
            Nabywca: DEMO BUYER NIP 7010060214
            Korekta razem: 0,00 0,00 0,00
            Razem do zapłaty: 0,00 PLN
            """);
    assertThat(correction.status())
        .as(correction.warnings().toString())
        .isEqualTo(ScanStatus.COMPLETE);
    assertThat(correction.invoice().documentType()).isEqualTo("CREDIT_NOTE");
    assertThat(correction.invoice().netAmount()).isZero();
    assertThat(correction.invoice().vatAmount()).isZero();
    assertThat(correction.invoice().grossAmount()).isZero();
  }

  @Test
  void doesNotCompleteReverseChargeDocumentWithoutAUsableTotal() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Invoice 1/3/2023
            Issue date 2023-03-31
            Seller: DEMO SELLER VAT ID 8133703437
            Buyer: DEMO BUYER
            Service 4,000.00 np 4,000.00
            Remarks: reverse charge
            """);

    assertThat(result.status()).isEqualTo(ScanStatus.PARTIAL);
    assertThat(result.invoice()).isNull();
    assertThat(result.warnings()).contains("missing net amount", "missing VAT amount");
  }

  @Test
  void keepsInvoiceCurrencyWhenTextAlsoContainsPlnConversion() {
    InvoiceTextParser.ParseResult result =
        parser.parse(
            """
            Invoice 1/3/2023
            Issue date 2023-03-31
            Seller: DEMO SELLER VAT ID 8133703437
            Buyer: DEMO BUYER
            Total in PLN: 17 217,20
            Total: 4,000.00 USD
            Remarks: reverse charge
            """);

    assertThat(result.status()).as(result.warnings().toString()).isEqualTo(ScanStatus.COMPLETE);
    assertThat(result.invoice().currency()).isEqualTo("USD");
    assertThat(result.invoice().grossAmount()).isEqualByComparingTo("4000.00");
  }
}
