package com.smartbox.investory.ui.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpAccountingRestClientTest {
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final HttpAccountingRestClient client =
      new HttpAccountingRestClient(builder, "http://localhost:8080");

  @AfterEach
  void cleanup() {
    RequestContextHolder.resetRequestAttributes();
    server.verify();
  }

  @Test
  void readsDtoOverHttp() {
    server
        .expect(
            requestTo("http://localhost:8080/api/profiles/1/accounting/months/2026-01/overview"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"month\":\"2026-01\",\"lifecycle\":\"OPEN\"}", MediaType.APPLICATION_JSON));

    assertThat(client.overview(1, YearMonth.of(2026, 1)).month()).isEqualTo(YearMonth.of(2026, 1));
  }

  @Test
  void sendsLifecycleActionAndForwardsAuthorization() {
    server
        .expect(requestTo("http://localhost:8080/api/profiles/1/accounting/months/2026-01/confirm"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andRespond(withSuccess());

    var request = new MockHttpServletRequest();
    request.addHeader(AUTHORIZATION, "Basic dXNlcjpwYXNz");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    client.confirm(1, YearMonth.of(2026, 1));
  }

  @Test
  void mapsApiFailureToClientException() {
    server
        .expect(requestTo("http://localhost:8080/api/profiles/1/accounting/months"))
        .andRespond(withBadRequest().body("period is invalid"));

    assertThatThrownBy(() -> client.months(1))
        .isInstanceOf(AccountingClientException.class)
        .hasMessageContaining("period is invalid");
  }
}
