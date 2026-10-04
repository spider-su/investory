package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.MediaRadarApi;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.domain.MediaSourceType;
import com.smartbox.investory.marketradar.domain.OpinionStance;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/market-radar/analyst-observations")
public class AnalystObservationAdminController {
  private final MediaRadarApi media;

  public AnalystObservationAdminController(MediaRadarApi media) {
    this.media = media;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void ingest(@RequestBody AnalystObservationRequest request) {
    media.ingest(
        new MediaObservation(
            request.externalId(),
            MediaSourceType.ANALYST,
            request.source(),
            request.author(),
            request.headline(),
            request.url(),
            request.publishedAt(),
            request.symbols(),
            request.stance()));
  }

  public record AnalystObservationRequest(
      String externalId,
      String source,
      String author,
      String headline,
      String url,
      Instant publishedAt,
      List<String> symbols,
      OpinionStance stance) {}
}
