package com.smartbox.investory.ryczalt.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankApi;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class RyczaltBankImportRestControllerTest {
  private final RyczaltBankApi bank = mock(RyczaltBankApi.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final RyczaltBankImportRestController controller =
      new RyczaltBankImportRestController(bank, authorization);

  @Test
  void rejectsUnsupportedBankUploadAtTheBoundary() {
    when(authorization.canWrite(1L, authentication)).thenReturn(true);

    assertThatThrownBy(
            () ->
                controller.importBank(
                    1L, "invoice.pdf", "application/pdf", "%PDF".getBytes(), authentication))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("CSV");
  }

  @Test
  void unexpectedBankFailureIsNotMappedToBadRequest() {
    when(authorization.canWrite(1L, authentication)).thenReturn(true);
    when(bank.importBank(eq(1L), any(byte[].class), eq("bank.csv"), eq("text/csv")))
        .thenThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(
            () ->
                controller.importBank(
                    1L, "bank.csv", "text/csv", "date,amount".getBytes(), authentication))
        .isInstanceOf(IllegalStateException.class);
  }
}
