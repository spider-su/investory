package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RealZusDraConfirmationClientTest {
  @Test
  void fetchesAndParsesConfirmationPackageFromEwd() throws Exception {
    byte[] confirmation = "upo-package".getBytes(StandardCharsets.UTF_8);
    String encoded = Base64.getEncoder().encodeToString(confirmation);
    AtomicReference<String> request = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/ewd",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response =
              ("""
                  <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:n="naws.zus.pl">
                    <s:Body><n:PobierzPotwierdzenieResponse>
                      <n:strIdZadania>TASK-1</n:strIdZadania>
                      <n:DataWpisu>2026-09-17T10:00:00Z</n:DataWpisu>
                      <n:strTyp>UPO</n:strTyp>
                      <n:uiWielkoscPrzesylki>10</n:uiWielkoscPrzesylki>
                      <n:byPrzesylka>%s</n:byPrzesylka>
                    </n:PobierzPotwierdzenieResponse></s:Body>
                  </s:Envelope>
                  """
                      .formatted(encoded))
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/soap+xml");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/ewd");
      var client =
          new RealZusDraConfirmationClient(
              endpoint, HttpClient.newHttpClient(), "Investory", "Investory", "0.1");

      ZusDraConfirmation result = client.fetch("SUBMISSION-1");

      assertThat(request.get()).contains("PobierzPotwierdzenie", "SUBMISSION-1");
      assertThat(result.submissionId()).isEqualTo("SUBMISSION-1");
      assertThat(result.taskId()).isEqualTo("TASK-1");
      assertThat(result.type()).isEqualTo("UPO");
      assertThat(result.packagePayload()).isEqualTo(confirmation);
      assertThat(result.packageHash()).isEqualTo(AccountingFilingFingerprint.sha256(confirmation));
      assertThat(result.rawSoapResponse()).contains((byte) '<');
    } finally {
      server.stop(0);
    }
  }
}
