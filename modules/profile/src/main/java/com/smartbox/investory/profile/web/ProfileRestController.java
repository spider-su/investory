package com.smartbox.investory.profile.web;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
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
  private final ProfileSnapshotReader profiles;

  @GetMapping
  public InvestmentProfile profile(@PathVariable @Positive Long portfolioId) {
    return profiles.loadProfile(portfolioId);
  }
}
