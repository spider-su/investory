package com.smartbox.investory.ui.accounting;

import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** HTTP adapter for the user-facing accounting API. */
@Component
public class HttpAccountingRestClient implements AccountingRestClient {
  private static final String API = "/api/profiles/{profileId}/accounting";
  private final RestClient client;

  public HttpAccountingRestClient(
      RestClient.Builder builder,
      @Value("${app.accounting.api-base-url:http://localhost:8080}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  public List<MonthRef> months(long p) {
    return get(API + "/months", new ParameterizedTypeReference<>() {}, p);
  }

  public MonthOverview overview(long p, YearMonth m) {
    return get(API + "/months/{month}/overview", MonthOverview.class, p, m);
  }

  public List<IssueView> issues(long p, YearMonth m) {
    return get(API + "/months/{month}/issues", new ParameterizedTypeReference<>() {}, p, m);
  }

  public List<DocumentView> documents(long p, YearMonth m) {
    return get(API + "/months/{month}/documents", new ParameterizedTypeReference<>() {}, p, m);
  }

  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    return get(
        API + "/months/{month}/bank-transactions", new ParameterizedTypeReference<>() {}, p, m);
  }

  public List<PaymentView> payments(long p, YearMonth m) {
    return get(API + "/months/{month}/payments", new ParameterizedTypeReference<>() {}, p, m);
  }

  public FilingView filings(long p, YearMonth m) {
    return get(API + "/months/{month}/filings", FilingView.class, p, m);
  }

  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    return get(API + "/months/{month}/reconciliation", new ParameterizedTypeReference<>() {}, p, m);
  }

  public CandidateView recognize(long p, String filename, String contentType, byte[] content) {
    return exchange(
        client
            .post()
            .uri(API + "/documents/recognize", p)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(
                new org.springframework.util.LinkedMultiValueMap<String, Object>() {
                  {
                    add(
                        "file",
                        new org.springframework.core.io.ByteArrayResource(content) {
                          @Override
                          public String getFilename() {
                            return filename;
                          }
                        });
                  }
                }),
        CandidateView.class);
  }

  public void saveReviewed(long p, ReviewedDocument d) {
    exchange(
        client.post().uri(API + "/documents", p).contentType(MediaType.APPLICATION_JSON).body(d),
        Void.class);
  }

  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    exchange(
        client
            .post()
            .uri(API + "/bank/import?month={month}", p, m)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(
                new org.springframework.util.LinkedMultiValueMap<String, Object>() {
                  {
                    add(
                        "file",
                        new org.springframework.core.io.ByteArrayResource(b) {
                          @Override
                          public String getFilename() {
                            return f;
                          }
                        });
                  }
                }),
        Void.class);
  }

  public void confirm(long p, YearMonth m) {
    postAction("confirm", p, m);
  }

  public void file(long p, YearMonth m) {
    postAction("file", p, m);
  }

  public void settle(long p, YearMonth m) {
    postAction("settle", p, m);
  }

  public void lock(long p, YearMonth m) {
    postAction("lock", p, m);
  }

  public void reopen(long p, YearMonth m, String r) {
    post(API + "/months/{month}/reopen?reason={reason}", p, m, r);
  }

  private void postAction(String action, long p, YearMonth m) {
    post(API + "/months/{month}/" + action, p, m);
  }

  private <T> T get(String uri, Class<T> type, Object... values) {
    return exchange(client.get().uri(uri, values), type);
  }

  private <T> T get(String uri, ParameterizedTypeReference<T> type, Object... values) {
    return exchange(client.get().uri(uri, values), type);
  }

  private void post(String uri, Object... values) {
    exchange(client.post().uri(uri, values), Void.class);
  }

  private <T> T exchange(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
    try {
      return request.headers(this::forwardAuthorization).retrieve().body(type);
    } catch (org.springframework.web.client.RestClientResponseException exception) {
      throw new AccountingClientException(
          exception.getStatusCode().value(), exception.getResponseBodyAsString());
    }
  }

  private <T> T exchange(
      RestClient.RequestHeadersSpec<?> request, ParameterizedTypeReference<T> type) {
    try {
      return request.headers(this::forwardAuthorization).retrieve().body(type);
    } catch (org.springframework.web.client.RestClientResponseException exception) {
      throw new AccountingClientException(
          exception.getStatusCode().value(), exception.getResponseBodyAsString());
    }
  }

  private void forwardAuthorization(HttpHeaders headers) {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes servlet) {
      String authorization = servlet.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
      if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
    }
  }
}
