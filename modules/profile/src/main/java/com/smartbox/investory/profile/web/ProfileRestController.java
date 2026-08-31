package com.smartbox.investory.profile.web;

import com.smartbox.investory.profile.api.ProfileSummaryReader;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for whole-wealth profile queries. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/profile")
@RequiredArgsConstructor
public class ProfileRestController {
  private final ProfileSummaryReader profiles;

  @GetMapping
  public ProfileResponse profile(@PathVariable @Positive Long portfolioId) {
    return ProfileResponse.from(profiles.loadSummary(portfolioId));
  }
}
