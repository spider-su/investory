package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated portfolio question endpoint. Scope is checked before reading ANY facts. */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/rag")
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class PortfolioRagController {
  private final AuthorizationService authorization;
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper mapper;
  private final InvestoryRagClient rag;
  private final RagUserRateLimiter rateLimiter;

  public PortfolioRagController(
      AuthorizationService authorization,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      InvestoryRagClient rag,
      RagUserRateLimiter rateLimiter) {
    this.authorization = authorization;
    this.profiles = profiles;
    this.mapper = mapper;
    this.rag = rag;
    this.rateLimiter = rateLimiter;
  }

  @PostMapping("/ask")
  public InvestoryRagClient.RagAnswer ask(
      @PathVariable long portfolioId,
      @RequestBody Question request,
      Authentication authentication) {
    if (portfolioId <= 0
        || request == null
        || request.question() == null
        || request.question().isBlank()
        || request.question().length() > 2000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid question or portfolio");
    }
    if (!authorization.canReadScoped(portfolioId, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    if (!rateLimiter.tryAcquire(authentication == null ? null : authentication.getName())) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RAG request limit exceeded");
    }
    var snapshot = mapper.map(profiles.loadProfile(portfolioId));
    try {
      return rag.ask(request.question(), snapshot);
    } catch (PythonRagClient.RagUnavailableException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RAG service unavailable");
    }
  }

  public record Question(String question) {}
}
