package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.domain.MediaSourceType;
import com.smartbox.investory.marketradar.domain.OpinionStance;
import com.smartbox.investory.marketradar.port.MediaSourcePort;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class YahooFinanceRssMediaSource implements MediaSourcePort {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String BASE_URL = "https://feeds.finance.yahoo.com/rss/2.0/headline?s=";

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public List<MediaObservation> fetch(List<String> symbols, Instant since) {
    List<MediaObservation> result = new ArrayList<>();
    for (String symbol : symbols) {
      result.addAll(fetchSymbol(symbol, since));
    }
    return List.copyOf(result);
  }

  private List<MediaObservation> fetchSymbol(String symbol, Instant since) {
    try {
      String url =
          BASE_URL + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "&region=US&lang=en-US";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(TIMEOUT)
              .header("User-Agent", "Investory/1.0")
              .GET()
              .build();
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() / 100 != 2) return List.of();
      return parse(symbol, response.body(), since);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (Exception e) {
      return List.of();
    }
  }

  private List<MediaObservation> parse(String symbol, byte[] xml, Instant since) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setExpandEntityReferences(false);

    var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    NodeList items = document.getElementsByTagName("item");
    List<MediaObservation> result = new ArrayList<>();
    for (int i = 0; i < items.getLength(); i++) {
      Element item = (Element) items.item(i);
      String title = text(item, "title");
      String link = text(item, "link");
      String guid = text(item, "guid");
      Instant published = published(text(item, "pubDate"));
      if (published == null || published.isBefore(since)) continue;
      String source = text(item, "source");
      if (source == null || source.isBlank()) source = "Yahoo Finance RSS";
      String externalId = guid == null || guid.isBlank() ? link : guid;
      if (externalId == null || externalId.isBlank() || title == null || title.isBlank()) continue;
      result.add(
          new MediaObservation(
              externalId,
              MediaSourceType.MEDIA,
              source,
              null,
              title,
              link,
              published,
              List.of(symbol),
              OpinionStance.UNKNOWN));
    }
    return result;
  }

  private String text(Element element, String tag) {
    NodeList nodes = element.getElementsByTagName(tag);
    if (nodes.getLength() == 0) return null;
    String value = nodes.item(0).getTextContent();
    return value == null ? null : value.trim();
  }

  private Instant published(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
