package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.math.BigDecimal;
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
      Pattern.compile(
          "(?i:ticker|symbol|price of|quote for)\\s+([A-Za-z][A-Za-z0-9.^-]{0,19})(?![A-Za-z0-9.^-])");
  private static final Pattern MARKET_LANGUAGE =
      Pattern.compile(
          "(?i)\\b(?:quote|ticker|trading|stock price|price of|latest price|market (?:price|quote|data|research))\\b");
  private static final Pattern RETIREMENT =
      Pattern.compile("(?i)\\b(?:retirement|retire|pension|financial independence)\\b");
  private static final Pattern HOLDINGS =
      Pattern.compile(
          "(?i)\\b(?:holdings|positions|allocation)\\b|\\b(?:main|largest|biggest|top|major|primary)\\s+(?:assets?|holdings?|positions?)\\b");
  private static final Pattern DOCUMENTS =
      Pattern.compile(
          "(?i)\\b(?:documentation|documents|methodology|method|explain|definition|docs|calculat\\w*)\\b");
  private static final Pattern SPY = Pattern.compile("(?i)\\b(?:spy|s&p ?500)\\b");
  private static final Pattern COMPARE =
      Pattern.compile("(?i)\\b(?:compare|compared|comparison|versus|vs|difference|performing)\\b");
  private static final Pattern NET_WORTH =
      Pattern.compile("(?i)\\b(?:net worth|wealth|how much am i worth)\\b");
  private static final Pattern MARKET_VALUE = Pattern.compile("(?i)\\bmarket portfolio value\\b");
  private static final Pattern LIQUID = Pattern.compile("(?i)\\bliquid assets?\\b");
  private static final Pattern ILLIQUID = Pattern.compile("(?i)\\billiquid assets?\\b");
  private static final Pattern LONG_TERM =
      Pattern.compile("(?i)\\blong[- ]term (?:assets?|value)\\b");
  private static final Pattern PERSONAL = Pattern.compile("(?i)\\b(?:my|me|i|our)\\b");

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
    boolean wantsDocs = DOCUMENTS.matcher(question).find();
    boolean wantsSpyComparison =
        intent == PortfolioAssistantReadModels.Intent.SPY_COMPARISON
            || (SPY.matcher(question).find() && COMPARE.matcher(question).find());
    boolean wantsProfile =
        (!wantsDocs || PERSONAL.matcher(question).find())
            && (NET_WORTH.matcher(question).find()
                || MARKET_VALUE.matcher(question).find()
                || LIQUID.matcher(question).find()
                || ILLIQUID.matcher(question).find()
                || LONG_TERM.matcher(question).find());
    if (wantsProfile) {
      var snapshot = mapper.map(profiles.loadProfile(portfolioId));
      if (intent == PortfolioAssistantReadModels.Intent.NET_WORTH
          || NET_WORTH.matcher(question).find())
        append(readModels.netWorth(snapshot), "JAVA_PROFILE", parts, citations, used);
      if (MARKET_VALUE.matcher(question).find())
        appendFact(
            "Market portfolio value",
            snapshot.marketPortfolioValue(),
            snapshot,
            parts,
            citations,
            used);
      if (LIQUID.matcher(question).find())
        appendFact("Liquid assets", snapshot.liquidAssets(), snapshot, parts, citations, used);
      if (ILLIQUID.matcher(question).find())
        appendFact("Illiquid assets", snapshot.illiquidAssets(), snapshot, parts, citations, used);
      if (LONG_TERM.matcher(question).find())
        appendFact(
            "Long-term assets", snapshot.longTermAssetValue(), snapshot, parts, citations, used);
    }
    if (wantsSpyComparison)
      append(readModels.spyComparison(portfolioId), "JAVA_BENCHMARK", parts, citations, used);
    if (intent == PortfolioAssistantReadModels.Intent.MAIN_ASSETS
        || HOLDINGS.matcher(question).find())
      append(readModels.mainAssets(portfolioId), "JAVA_HOLDINGS", parts, citations, used);
    if (wantsRetirement) {
      append(readModels.retirementPlan(portfolioId), "JAVA_RETIREMENT", parts, citations, used);
    }
    if (profitability.supports(question)) {
      append(profitability.answer(portfolioId), "JAVA_PROFITABILITY", parts, citations, used);
    }
    if (wantsDocs) {
      try {
        append(
            rag.askDocumentation(documentationQuestion(question, wantsProfile, wantsRetirement)),
            "RAG_DOCUMENTATION",
            parts,
            citations,
            used);
      } catch (PythonRagClient.RagUnavailableException e) {
        parts.add("Documentation is temporarily unavailable.");
      }
    }

    // Explicit public-market request only. Never send account, position size or user data to MCP.
    InvestoryRagClient.MarketData observation = null;
    if (wantsMarket) {
      Matcher matcher = PUBLIC_SYMBOL.matcher(question);
      Matcher explicit =
          Pattern.compile("(?i:\\b(?:ticker|symbol))\\s+([A-Za-z][A-Za-z0-9.^-]{0,19})")
              .matcher(question);
      String symbol =
          explicit.find() ? explicit.group(1) : matcher.find() ? matcher.group(1) : null;
      if (symbol != null) symbol = symbol.toUpperCase(Locale.ROOT).replaceAll("\\.$", "");
      if (symbol != null
          && List.of("MY", "THE", "OUR", "A", "AN", "TICKER", "SYMBOL").contains(symbol))
        symbol = null;
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
            observation = quote;
            String citation = "market:alpha-vantage:" + symbol;
            parts.add(
                "Public provider observation for "
                    + symbol
                    + " is attached separately; it is untrusted research, not an Investory holding valuation. ["
                    + citation
                    + "]");
            citations.add(citation);
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
    return new Answer(
        String.join("\n\n", parts),
        List.copyOf(citations),
        List.copyOf(new LinkedHashSet<>(used)),
        observation);
  }

  private static void append(
      InvestoryRagClient.RagAnswer answer,
      String source,
      List<String> parts,
      LinkedHashSet<String> citations,
      List<String> used) {
    if (answer == null || answer.answer() == null || answer.answer().isBlank()) return;
    parts.add(
        source.equals("RAG_DOCUMENTATION")
            ? "Public documentation: " + answer.answer()
            : answer.answer());
    if (answer.sources() != null) citations.addAll(answer.sources());
    used.add(answer.sources() == null || answer.sources().isEmpty() ? "ABSTAINED" : source);
  }

  private static String documentationQuestion(
      String question, boolean profile, boolean retirement) {
    Matcher docs = DOCUMENTS.matcher(question);
    if (docs.find() && docs.start() > 0) {
      String clause = question.substring(docs.start());
      if (!PERSONAL.matcher(clause).find()) return clause;
    }
    if (profile) {
      List<String> topics = new ArrayList<>();
      if (NET_WORTH.matcher(question).find()) topics.add("net worth");
      if (MARKET_VALUE.matcher(question).find()) topics.add("market portfolio value");
      if (LIQUID.matcher(question).find()) topics.add("liquid assets");
      if (ILLIQUID.matcher(question).find()) topics.add("illiquid assets");
      if (LONG_TERM.matcher(question).find()) topics.add("long-term assets");
      return "How does Investory calculate " + String.join(" and ", topics) + "?";
    }
    if (retirement && PERSONAL.matcher(question).find())
      return "Explain retirement plan methodology and withdrawal order";
    return question;
  }

  private static void appendFact(
      String label,
      BigDecimal value,
      ProfileResponse snapshot,
      List<String> parts,
      LinkedHashSet<String> citations,
      List<String> used) {
    if (value == null || snapshot.currency() == null) {
      parts.add(label + " is unavailable from the current profile.");
      used.add("ABSTAINED");
      return;
    }
    parts.add(
        label
            + ": "
            + value.toPlainString()
            + " "
            + snapshot.currency().name()
            + ". [live:profile]");
    citations.add("live:profile");
    used.add("JAVA_PROFILE");
  }

  public record Question(String question) {}

  public record Answer(
      String answer,
      List<String> sources,
      List<String> components,
      InvestoryRagClient.MarketData marketData) {
    public Answer(String answer, List<String> sources, List<String> components) {
      this(answer, sources, components, null);
    }
  }
}
