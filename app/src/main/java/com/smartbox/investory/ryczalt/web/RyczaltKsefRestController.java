package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefSyncResult;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting/ksef")
public class RyczaltKsefRestController {
  private final RyczaltKsefApi ksef;
  private final AuthorizationService authorization;

  public RyczaltKsefRestController(RyczaltKsefApi ksef, AuthorizationService authorization) {
    this.ksef = ksef;
    this.authorization = authorization;
  }

  @PostMapping("/sync")
  public RyczaltKsefSyncResult sync(
      @PathVariable long profileId,
      @RequestBody SyncRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    if (request == null || request.month() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month is required");
    Set<KsefSyncMode> modes =
        request.modes() == null || request.modes().isEmpty()
            ? Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES)
            : request.modes();
    return ksef.sync(profileId, request.month(), modes);
  }

  private void write(long profileId, Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record SyncRequest(YearMonth month, Set<KsefSyncMode> modes) {}
}
