package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
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

/**
 * First-stage, read-only financial AI orchestrator.
 *
 * <p>Source selection is deterministic, not LLM-generated tool calls. Java owns authorization,
 * portfolio calculations, and user facts; market lookup receives only an allowlisted public symbol.
 * The response preserves provenance and never presents provider quotes as portfolio valuations.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/assistant")
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class FinancialAiOrchestratorController {
  private static final Pattern PUBLIC_SYMBOL =
      Pattern.compile("\\b(?:ticker|symbol|price of|quote for)\\s+([A-Z][A-Z0-9.^-]{0,19})\\b");
  private static final Pattern MARKET_LANGUAGE =
      Pattern.compile("(?i)\\b(?:latest|market|quote|price|trading|ticker|stock)\\b");
  private static final Pattern RETIREMENT =
      Pattern.compile("(?i)\\b(?:retirement|retire|pension|financial independence)\\b");
  private static final Pattern PORTFOLIO =
      Pattern.compile(
          "(?i)\\b(?:portfolio|holdings|positions|assets|allocation|investment|net worth|wealth)\\b");
  private static final Pattern HOLDINGS =
      Pattern.compile(
          "(?i)\\b(?:holdings|positions|allocation|assets|largest|biggest|top)\\b");
  private static final Pattern DOCUMENTS =
      Pattern.compile(
          "(?i)\\b(?:documentation|documents|methodology|method|explain|definition|docs)\\b");
  private static final Pattern SPY = Pattern.compile("(?i)\\b(?:spy|s&p ?500)\\b");
  private static final Pattern COMPARE =
      Pattern.compile("(?i)\\b(?:compare|compared|comparison|versus|vs|difference|performing)\\b");

  private final AuthorizationService authorization;
  private final RagUserRateLimiter rateLimiter;
  private final RagConcurrencyLimiter concurrency;
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper mapper;
  private final PortfolioAssistantReadModels readModels;
  private final PortfolioProfitabilityAnswer profitability;
  private final InvestoryRagClient rag;
  private final ExternalMarketDataService market;

  public FinancialAiOrchestratorController(
      AuthorizationService authorization,
      RagUserRateLimiter rateLimiter,
      RagConcurrencyLimiter concurrency,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      PortfolioAssistantReadModels readModels,
      PortfolioProfitabilityAnswer profitability,
      InvestoryRagClient rag,
      ExternalMarketDataService market) {
    this.authorization = authorization;
    this.rateLimiter = rateLimiter;
    this.concurrency = concurrency;
    this.profiles = profiles;
    this.mapper = mapper;
    this.readModels = readModels;
    this.profitability = profitability;
    this.rag = rag;
    this.market = market;
  }

  @PostMapping("/ask")
  public Answer ask(
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
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Assistant request limit exceeded");
    }
    if (!concurrency.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Assistant is busy");
    }
    try {
      return answerAuthorized(portfolioId, request.question());
    } finally {
      concurrency.release();
    }
  }

  private Answer answerAuthorized(long portfolioId, String question) {
    var intent = readModels.classify(question);
    if (intent == PortfolioAssistantReadModels.Intent.UNSUPPORTED) {
      return new Answer(
          "The requested period, prediction, or trading decision is not supported by verified data.",
          List.of(),
          List.of("ABSTAINED"));
    }

    List<String> parts = new ArrayList<>();
    LinkedHashSet<String> citations = new LinkedHashSet<>();
    List<String> used = new ArrayList<>();
    boolean wantsMarket = MARKET_LANGUAGE.matcher(question).find();
    boolean wantsRetirement = RETIREMENT.matcher(question).find();
    boolean wantsPortfolio = PORTFOLIO.matcher(question).find();
    boolean wantsDocs = DOCUMENTS.matcher(question).find();
    boolean wantsSpyComparison = SPY.matcher(question).find() && COMPARE.matcher(question).find();

    // Each domain read is scoped to the same previously-authorized portfolio.
    if (wantsSpyComparison) {
      append(readModels.spyComparison(portfolioId), "JAVA_BENCHMARK", parts, citations, used);
    } else if (intent == PortfolioAssistantReadModels.Intent.NET_WORTH) {
      append(
          readModels.netWorth(mapper.map(profiles.loadProfile(portfolioId))),
          "JAVA_PROFILE",
          parts,
          citations,
          used);
    } else if (wantsPortfolio && HOLDINGS.matcher(question).find()) {
      append(readModels.mainAssets(portfolioId), "JAVA_HOLDINGS", parts, citations, used);
    }
    if (wantsRetirement) {
      append(readModels.retirementPlan(portfolioId), "JAVA_RETIREMENT", parts, citations, used);
    }
    if (profitability.supports(question) && !wantsSpyComparison) {
      append(profitability.answer(portfolioId), "JAVA_PROFITABILITY", parts, citations, used);
    }
    if (wantsDocs) {
      try {
        append(rag.askDocumentation(question), "RAG_DOCUMENTATION", parts, citations, used);
      } catch (PythonRagClient.RagUnavailableException e) {
        parts.add("Documentation is temporarily unavailable.");
      }
    }

    // Explicit public-market request only. Never send account, position size or user data to MCP.
    if (wantsMarket) {
      Matcher matcher = PUBLIC_SYMBOL.matcher(question);
      String symbol = matcher.find() ? matcher.group(1) : null;
      if (symbol == null && SPY.matcher(question).find()) symbol = "SPY";
      if (symbol == null
          && question
              .toLowerCase(Locale.ROOT)
              .matches(".*\\b(?:largest|biggest|top)\\s+holding\\b.*")) {
        symbol = readModels.largestHoldingSymbol(portfolioId);
      }
      if (symbol != null && symbol.matches("[A-Z0-9][A-Z0-9.^-]{0,19}")) {
        try {
          var quote = market.lookup("GLOBAL_QUOTE", symbol);
          if (quote != null && quote.data() != null && !quote.data().isBlank()) {
            parts.add(
                "Public market data for "
                    + symbol
                    + " ("
                    + quote.tool()
                    + "): "
                    + quote.data()
                    + ". Source: "
                    + quote.source()
                    + ". This is a provider observation, not an Investory holding valuation.");
            citations.add("market:" + quote.provider() + ":" + symbol);
            used.add("MARKET_MCP");
          }
        } catch (PythonRagClient.MarketDataUnavailableException
            | PythonRagClient.MarketDataNotEntitledException e) {
          parts.add("Public market data for " + symbol + " is unavailable from the provider.");
        } catch (ExternalMarketDataService.BudgetExceededException
            | ExternalMarketDataService.BusyException e) {
          parts.add(
              "Public market lookup was skipped because its quota or concurrency limit was reached.");
        } catch (PythonRagClient.MarketResearchUnavailableException
            | PythonRagClient.RagUnavailableException e) {
          parts.add("Public market lookup is temporarily unavailable.");
        }
      } else {
        parts.add(
            "Market research requires an explicit supported public symbol or the largest holding.");
      }
    }

    // Preserve the existing authenticated RAG route for queries without an explicit domain route.
    if (parts.isEmpty()) {
      try {
        append(
            rag.ask(question, mapper.map(profiles.loadProfile(portfolioId))),
            "RAG_PROFILE",
            parts,
            citations,
            used);
      } catch (PythonRagClient.RagUnavailableException e) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "RAG service unavailable");
      }
    }
    return new Answer(String.join("\n\n", parts), List.copyOf(citations), List.copyOf(used));
  }

  private static void append(
      InvestoryRagClient.RagAnswer answer,
      String source,
      List<String> parts,
      LinkedHashSet<String> citations,
      List<String> used) {
    if (answer == null || answer.answer() == null || answer.answer().isBlank()) return;
    parts.add(answer.answer());
    if (answer.sources() != null) citations.addAll(answer.sources());
    used.add(source);
  }

  public record Question(String question) {}

  public record Answer(String answer, List<String> sources, List<String> components) {}
}
