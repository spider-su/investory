package com.smartbox.investory.profile.web;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** HTTP adapter for whole-wealth profile queries. */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/profile")
@RequiredArgsConstructor
public class ProfileRestController {
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper responseMapper;

  @GetMapping
  public ProfileResponse profile(@PathVariable Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portfolioId must be positive");
    }
    InvestmentProfile profile = profiles.loadProfile(portfolioId);
    return responseMapper.map(profile);
  }
}
