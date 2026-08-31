package com.smartbox.investory.ui.longterm;

import static org.mockito.Mockito.verify;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("In Process Long Term Assets Client")
class InProcessLongTermAssetsClientTest {
  @Mock private LongTermAssetsApi api;

  @DisplayName("delegates To Public Api Without Http")
  @Test
  void delegatesThroughRestFacadeWithoutHttp() {
    LocalDate date = LocalDate.of(2026, 8, 26);
    var client = new InProcessLongTermAssetsClient(api);

    client.page(1L, date);

    verify(api).page(1L, date);
  }
}
