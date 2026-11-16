package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.util.Set;
import java.util.regex.Pattern;
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
  private final RagConcurrencyLimiter concurrencyLimiter;
  private final PortfolioProfitabilityAnswer profitability;
  private final PortfolioAssistantReadModels readModels;

  public PortfolioRagController(
      AuthorizationService authorization,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      InvestoryRagClient rag,
      RagUserRateLimiter rateLimiter,
      RagConcurrencyLimiter concurrencyLimiter,
      PortfolioProfitabilityAnswer profitability,
      PortfolioAssistantReadModels readModels) {
    this.authorization = authorization;
    this.profiles = profiles;
    this.mapper = mapper;
    this.rag = rag;
    this.rateLimiter = rateLimiter;
    this.concurrencyLimiter = concurrencyLimiter;
    this.profitability = profitability;
    this.readModels = readModels;
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
    if (!concurrencyLimiter.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RAG service is busy");
    }
    try {
      var intent = readModels.classify(request.question());
      if (intent == PortfolioAssistantReadModels.Intent.UNSUPPORTED) {
        return new InvestoryRagClient.RagAnswer(
            "The selected reporting data cannot answer that requested period or forecast.",
            java.util.List.of());
      }
      if (intent == PortfolioAssistantReadModels.Intent.NET_WORTH) {
        return readModels.netWorth(mapper.map(profiles.loadProfile(portfolioId)));
      }
      if (intent == PortfolioAssistantReadModels.Intent.MAIN_ASSETS) {
        return readModels.mainAssets(portfolioId);
      }
      if (intent == PortfolioAssistantReadModels.Intent.SPY_COMPARISON) {
        return readModels.spyComparison(portfolioId);
      }
      if (intent == PortfolioAssistantReadModels.Intent.RETIREMENT_PLAN) {
        return readModels.retirementPlan(portfolioId);
      }
      if (profitability.supports(request.question())) {
        return profitability.answer(portfolioId);
      }
      var snapshot = mapper.map(profiles.loadProfile(portfolioId));
      return rag.ask(request.question(), snapshot);
    } catch (PythonRagClient.RagUnavailableException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RAG service unavailable");
    } finally {
      concurrencyLimiter.release();
    }
  }

  /** Authorization and concurrency guards are identical, but no financial snapshot is loaded. */
  @PostMapping("/ask-docs")
  public InvestoryRagClient.RagAnswer askDocumentation(
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
    if (!concurrencyLimiter.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RAG service is busy");
    }
    try {
      return rag.askDocumentation(request.question());
    } catch (PythonRagClient.RagUnavailableException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RAG service unavailable");
    } finally {
      concurrencyLimiter.release();
    }
  }

  /** Allowlisted public ticker research. Deliberately separate from model answer generation. */
  @PostMapping("/research/market-data")
  public InvestoryRagClient.MarketData researchMarketData(
      @PathVariable long portfolioId,
      @RequestBody MarketResearchRequest request,
      Authentication authentication) {
    if (portfolioId <= 0 || request == null || !validMarketRequest(request)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid market research request");
    }
    checkMarketAccess(portfolioId, authentication);
    try {
      return rag.marketData(request.tool(), request.symbol());
    } catch (PythonRagClient.MarketDataNotEntitledException e) {
      throw new ResponseStatusException(
          HttpStatus.FAILED_DEPENDENCY, "Requested market data is not available from the provider");
    } catch (PythonRagClient.MarketResearchUnavailableException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Market research unavailable");
    } catch (PythonRagClient.RagUnavailableException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market research unavailable");
    } finally {
      concurrencyLimiter.release();
    }
  }

  /** Only the public ticker of the largest scoped holding leaves Java. */
  @PostMapping("/research/largest-holding")
  public InvestoryRagClient.MarketData researchLargestHolding(
      @PathVariable long portfolioId, Authentication authentication) {
    if (portfolioId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid portfolio");
    }
    checkMarketAccess(portfolioId, authentication);
    try {
      String symbol = readModels.largestHoldingSymbol(portfolioId);
      if (symbol == null || !VALID_SYMBOL.matcher(symbol).matches()) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "No supported public ticker for the largest holding");
      }
      try {
        return rag.marketData("COMPANY_OVERVIEW", symbol);
      } catch (PythonRagClient.MarketDataNotEntitledException e) {
        // The returned tool is explicit: a quote is not company fundamentals.
        return rag.marketData("GLOBAL_QUOTE", symbol);
      }
    } catch (PythonRagClient.MarketDataNotEntitledException e) {
      throw new ResponseStatusException(
          HttpStatus.FAILED_DEPENDENCY, "Requested market data is not available from the provider");
    } catch (PythonRagClient.MarketResearchUnavailableException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Market research unavailable");
    } catch (PythonRagClient.RagUnavailableException e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market research unavailable");
    } finally {
      concurrencyLimiter.release();
    }
  }

  private static final Set<String> MARKET_TOOLS =
      Set.of(
          "GLOBAL_QUOTE", "COMPANY_OVERVIEW", "INCOME_STATEMENT", "EARNINGS", "EARNINGS_CALENDAR");
  private static final Pattern VALID_SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9.^-]{0,19}");

  private static boolean validMarketRequest(MarketResearchRequest request) {
    return request.tool() != null
        && MARKET_TOOLS.contains(request.tool())
        && request.symbol() != null
        && VALID_SYMBOL.matcher(request.symbol()).matches();
  }

  private void checkMarketAccess(long portfolioId, Authentication authentication) {
    if (!authorization.canReadScoped(portfolioId, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    if (!rateLimiter.tryAcquire(authentication == null ? null : authentication.getName())) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RAG request limit exceeded");
    }
    if (!concurrencyLimiter.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "RAG service is busy");
    }
  }

  public record MarketResearchRequest(String tool, String symbol) {}

  public record Question(String question) {}
}
