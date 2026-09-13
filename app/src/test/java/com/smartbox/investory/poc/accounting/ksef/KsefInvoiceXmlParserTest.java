package com.smartbox.investory.poc.accounting.ksef;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KsefInvoiceXmlParserTest {

  private final KsefInvoiceXmlParser parser = new KsefInvoiceXmlParser();

  @Test
  void parsesCommonFaTotalsAndPartiesWithoutDependingOnNamespaceVersion() {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Faktura xmlns="http://crd.gov.pl/wzor/example/fa3">
          <Naglowek><KodFormularza>FA</KodFormularza></Naglowek>
          <Podmiot1><DaneIdentyfikacyjne><NIP>1111111111</NIP><Nazwa>Seller</Nazwa></DaneIdentyfikacyjne></Podmiot1>
          <Podmiot2><DaneIdentyfikacyjne><NIP>2222222222</NIP><Nazwa>Buyer</Nazwa></DaneIdentyfikacyjne></Podmiot2>
          <Fa>
            <KodWaluty>PLN</KodWaluty>
            <P_1>2026-08-29</P_1>
            <P_2>FV/8/2026</P_2>
            <P_6>2026-08-29</P_6>
            <P_13_1>100.00</P_13_1>
            <P_14_1>23.00</P_14_1>
            <P_13_2>50.00</P_13_2>
            <P_14_2>4.00</P_14_2>
            <P_15>177.00</P_15>
          </Fa>
        </Faktura>
        """;

    var result = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

    assertThat(result.reference()).isEqualTo("FV/8/2026");
    assertThat(result.issueDate()).hasToString("2026-08-29");
    assertThat(result.saleDate()).hasToString("2026-08-29");
    assertThat(result.sellerNip()).isEqualTo("1111111111");
    assertThat(result.buyerNip()).isEqualTo("2222222222");
    assertThat(result.currency()).isEqualTo("PLN");
    assertThat(result.netAmount()).isEqualByComparingTo("150.00");
    assertThat(result.vatAmount()).isEqualByComparingTo("27.00");
    assertThat(result.grossAmount()).isEqualByComparingTo("177.00");
    assertThat(result.category()).isNull();
    assertThat(result.vatDeductionRatio()).isNull();
  }

  @Test
  void classifiesOnlyExplicitlyRecognizedPurchaseDescriptions() {
    String xml =
        "<Faktura xmlns=\"urn:test\"><Fa><P_7>paliwo BP</P_7>"
            + "<P_13_1>100</P_13_1><P_14_1>23</P_14_1><P_15>123</P_15></Fa></Faktura>";

    var result = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

    assertThat(result.category()).isEqualTo("VEHICLE_FUEL");
    assertThat(result.vatDeductionRatio()).isEqualByComparingTo("0.50");
  }
}
