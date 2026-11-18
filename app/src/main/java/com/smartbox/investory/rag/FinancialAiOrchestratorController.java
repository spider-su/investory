package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
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
          "(?i:ticker|symbol|price of|quote for)\\s+([A-Za-z][A-Za-z0-9.^-]{0,19})(?![A-Za-z0-9._^/-])");
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

  private static final Pattern REPORTING_PERIOD =
      Pattern.compile(
          "(?i)\\b(?:YTD|year[- ]to[- ]date|this year|current year|1M|3M|6M|1Y|3Y|5Y|MAX)\\b");
  private static final Pattern OTHER_WINDOW =
      Pattern.compile(
          "(?i)\\b(?:mtd|qtd|today|yesterday|tomorrow|forecast|predict|will|all[ -]?time|lifetime|since inception|from the beginning|(?:19|20)\\d{2}|"
              + "(?:since|through|before|after|as of)\\s+\\d|(?:last|past|previous|next|this|current)\\s+(?:\\d+\\s+)?(?:day|week|month|quarter|year)s?)\\b");

  private static final String PERIOD_TOKEN = "(1M|3M|6M|YTD|1Y|3Y|5Y|MAX)";
  private static final Pattern FOLLOW_UP =
      Pattern.compile("(?i)^(compare that with|show that for) " + PERIOD_TOKEN + "[?.]?$");
  private static final Pattern ASSET_RESULT =
      Pattern.compile(
          "(?i)^show realized profit for asset ([A-Z0-9][A-Z0-9._^-]{0,31}) for "
              + PERIOD_TOKEN
              + "[?.]?$");
  private static final Pattern CONTEXT_REFERENCE =
      Pattern.compile("(?i)^(?:compare|show) (?:that|it)\\b");

  private final AuthorizationService authorization;
  private final RagUserRateLimiter rateLimiter;
  private final RagConcurrencyLimiter concurrency;
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper mapper;
  private final PortfolioAssistantReadModels readModels;
  private final PortfolioProfitabilityAnswer profitability;
  private final InvestoryRagClient rag;
  private final ExternalMarketDataService market;
  private final InvestmentAssetApi assets;
  private final AssistantMetrics metrics;

  public FinancialAiOrchestratorController(
      AuthorizationService authorization,
      RagUserRateLimiter rateLimiter,
      RagConcurrencyLimiter concurrency,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      PortfolioAssistantReadModels readModels,
      PortfolioProfitabilityAnswer profitability,
      InvestoryRagClient rag,
      ExternalMarketDataService market,
      InvestmentAssetApi assets,
      AssistantMetrics metrics) {
    this.authorization = authorization;
    this.rateLimiter = rateLimiter;
    this.concurrency = concurrency;
    this.profiles = profiles;
    this.mapper = mapper;
    this.readModels = readModels;
    this.profitability = profitability;
    this.rag = rag;
    this.market = market;
    this.assets = assets;
    this.metrics = metrics;
  }

  @PostMapping("/ask")
  public Answer ask(
      @PathVariable long portfolioId,
      @RequestBody Question request,
      Authentication authentication) {
    long started = System.nanoTime();
    var outcome = AssistantMetrics.RequestOutcome.ERROR;
    try {
      var answer = execute(portfolioId, request, authentication);
      metrics.routing(answer.components());
      outcome =
          answer.sources().isEmpty() && answer.marketData() == null
              ? AssistantMetrics.RequestOutcome.ABSTAINED
              : AssistantMetrics.RequestOutcome.ANSWERED;
      return answer;
    } catch (ResponseStatusException e) {
      outcome =
          switch (e.getStatusCode().value()) {
            case 400 -> AssistantMetrics.RequestOutcome.INVALID;
            case 403 -> AssistantMetrics.RequestOutcome.DENIED;
            case 429 -> AssistantMetrics.RequestOutcome.LIMITED;
            default -> AssistantMetrics.RequestOutcome.ERROR;
          };
      throw e;
    } finally {
      metrics.request(outcome, started);
    }
  }

  private Answer execute(long portfolioId, Question request, Authentication authentication) {
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
    if (request.context() != null && !request.context().validFor(portfolioId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid follow-up context");
    }
    if (!rateLimiter.tryAcquire(authentication == null ? null : authentication.getName())) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Assistant request limit exceeded");
    }
    if (!concurrency.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Assistant is busy");
    }
    try {
      String question = request.question().strip();
      var followUp = FOLLOW_UP.matcher(question);
      if (followUp.matches()) {
        if (request.context() == null) return missingContext();
        return followUp(
            request.context(),
            parsePeriod(followUp.group(2)),
            followUp.group(1).equalsIgnoreCase("compare that with"));
      }
      if (CONTEXT_REFERENCE.matcher(question).find()) return missingContext();
      var asset = ASSET_RESULT.matcher(question);
      if (asset.matches()) {
        return report(
            new Context(
                portfolioId,
                Topic.ASSET_REALIZED,
                asset.group(1).toUpperCase(Locale.ROOT),
                parsePeriod(asset.group(2))));
      }
      return answerAuthorized(portfolioId, question);
    } finally {
      concurrency.release();
    }
  }

  private Answer answerAuthorized(long portfolioId, String question) {
    Matcher requestedPeriod = REPORTING_PERIOD.matcher(question);
    if (requestedPeriod.find()) {
      String label = requestedPeriod.group();
      String remaining =
          question.substring(0, requestedPeriod.start())
              + " "
              + question.substring(requestedPeriod.end());
      if (requestedPeriod.find()
          || OTHER_WINDOW.matcher(remaining).find()
          || readModels.classify(remaining) == PortfolioAssistantReadModels.Intent.UNSUPPORTED
          || HOLDINGS.matcher(remaining).find()
          || RETIREMENT.matcher(remaining).find()
          || MARKET_LANGUAGE.matcher(remaining).find()
          || NET_WORTH.matcher(remaining).find()
          || MARKET_VALUE.matcher(remaining).find()
          || LIQUID.matcher(remaining).find()
          || ILLIQUID.matcher(remaining).find()
          || LONG_TERM.matcher(remaining).find()
          || DOCUMENTS.matcher(remaining).find()) {
        return unsupported();
      }
      var period =
          label.length() <= 3
              ? DashboardPeriod.fromUrlValue(label.toUpperCase(Locale.ROOT))
              : DashboardPeriod.YEAR_TO_DATE;
      if (SPY.matcher(remaining).find() && COMPARE.matcher(remaining).find()) {
        return report(new Context(portfolioId, Topic.SPY_BENCHMARK, null, period));
      }
      if (profitability.requestsResult(remaining)) {
        return report(new Context(portfolioId, Topic.PORTFOLIO_PROFIT, null, period));
      }
      return unsupported();
    }
    var intent = readModels.classify(question);
    if (intent == PortfolioAssistantReadModels.Intent.UNSUPPORTED) {
      return unsupported();
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
    InvestoryRagClient.MarketStatus marketStatus = null;
    if (wantsMarket) {
      Matcher matcher = PUBLIC_SYMBOL.matcher(question);
      Matcher explicit =
          Pattern.compile(
                  "(?i:\\b(?:ticker|symbol))\\s+([A-Za-z][A-Za-z0-9.^-]{0,19})(?![A-Za-z0-9._^/-])")
              .matcher(question);
      String symbol =
          explicit.find() ? explicit.group(1) : matcher.find() ? matcher.group(1) : null;
      if (symbol != null) symbol = symbol.toUpperCase(Locale.ROOT).replaceAll("\\.$", "");
      if (symbol != null
          && List.of("MY", "THE", "OUR", "A", "AN", "TICKER", "SYMBOL").contains(symbol))
        symbol = null;
      boolean holding = false;
      if (symbol == null && SPY.matcher(question).find()) symbol = "SPY";
      if (symbol == null
          && question
              .toLowerCase(Locale.ROOT)
              .matches(".*\\b(?:largest|biggest|top)\\s+holding\\b.*")) {
        symbol = readModels.largestHoldingSymbol(portfolioId);
        holding = true;
      }
      if (symbol != null && symbol.matches("[A-Z0-9][A-Z0-9.^-]{0,19}")) {
        try {
          var quote =
              holding
                  ? market.lookupHolding("GLOBAL_QUOTE", symbol)
                  : market.lookup("GLOBAL_QUOTE", symbol);
          if (quote != null && quote.quote() != null) {
            observation = quote.withoutRawData();
            String citation = "market:alpha-vantage:" + quote.symbol();
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
        marketStatus = market.quoteStatus(symbol, holding);
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
        observation,
        marketStatus,
        citations.isEmpty() || used.size() != 1 || parts.size() != 1
            ? null
            : switch (used.getFirst()) {
              case "JAVA_BENCHMARK" ->
                  new Context(portfolioId, Topic.SPY_BENCHMARK, null, DashboardPeriod.MAX);
              case "JAVA_PROFITABILITY" ->
                  new Context(portfolioId, Topic.PORTFOLIO_PROFIT, null, DashboardPeriod.MAX);
              default -> null;
            });
  }

  private static Answer unsupported() {
    return new Answer(
        "The requested period, prediction, or trading decision is not supported by verified data.",
        List.of(),
        List.of("ABSTAINED"));
  }

  private static DashboardPeriod parsePeriod(String text) {
    return DashboardPeriod.fromUrlValue(text.toUpperCase(Locale.ROOT));
  }

  private static Answer missingContext() {
    return new Answer(
        "Choose a portfolio profit, SPY comparison or asset realized profit report first. "
            + "Use “compare that with YTD” or “show that for YTD” to change its reporting period.",
        List.of(),
        List.of("ABSTAINED"));
  }

  private Answer followUp(Context selected, DashboardPeriod target, boolean compare) {
    var next = new Context(selected.portfolioId(), selected.topic(), selected.asset(), target);
    if (!compare || target == selected.period()) return report(next, true);
    var previous = report(selected, true);
    var updated = report(next, true);
    var citations = new LinkedHashSet<>(previous.sources());
    citations.addAll(updated.sources());
    var components = new LinkedHashSet<>(previous.components());
    components.addAll(updated.components());
    return new Answer(
        previous.answer() + "\n\n" + updated.answer(),
        List.copyOf(citations),
        List.copyOf(components),
        null,
        null,
        updated.context());
  }

  private Answer report(Context selected) {
    return report(selected, false);
  }

  private Answer report(Context selected, boolean freshFacts) {
    long portfolioId = selected.portfolioId();
    var period = selected.period();
    var result =
        switch (selected.topic()) {
          case PORTFOLIO_PROFIT ->
              freshFacts
                  ? profitability.answer(portfolioId, period, true)
                  : profitability.answer(portfolioId, period);
          case SPY_BENCHMARK ->
              freshFacts
                  ? readModels.spyComparison(portfolioId, period, true)
                  : readModels.spyComparison(portfolioId, period);
          case ASSET_REALIZED -> assetRealized(portfolioId, selected.asset(), period);
        };
    if (result.sources().isEmpty())
      return new Answer(result.answer(), List.of(), List.of("ABSTAINED"));
    return new Answer(
        result.answer(),
        result.sources(),
        List.of(selected.topic().component),
        null,
        null,
        selected);
  }

  private InvestoryRagClient.RagAnswer assetRealized(
      long portfolioId, String symbol, DashboardPeriod period) {
    try {
      var view = assets.detail(portfolioId, symbol, period);
      var amount = view.totalRealizedProfitLoss();
      if (amount != null && Double.isFinite(amount) && view.currency() != null) {
        return new InvestoryRagClient.RagAnswer(
            view.symbol()
                + " realized profit/loss for "
                + period.wireValue()
                + " is "
                + BigDecimal.valueOf(amount).toPlainString()
                + " "
                + view.currency().name()
                + ". [live:investment-asset]",
            List.of("live:investment-asset"));
      }
    } catch (InvestmentAssetApi.AssetNotFoundException ignored) {
      // The requested canonical symbol has no report.
    }
    return new InvestoryRagClient.RagAnswer(
        "Asset realized profit/loss for " + period.wireValue() + " is unavailable.", List.of());
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

  public enum Topic {
    PORTFOLIO_PROFIT("JAVA_PROFITABILITY"),
    SPY_BENCHMARK("JAVA_BENCHMARK"),
    ASSET_REALIZED("JAVA_ASSET");

    private final String component;

    Topic(String component) {
      this.component = component;
    }
  }

  public record Context(long portfolioId, Topic topic, String asset, DashboardPeriod period) {
    boolean validFor(long authorizedPortfolioId) {
      return portfolioId == authorizedPortfolioId
          && topic != null
          && period != null
          && (topic == Topic.ASSET_REALIZED
              ? asset != null && asset.matches("[A-Z0-9][A-Z0-9._^-]{0,31}")
              : asset == null);
    }
  }

  public record Question(String question, Context context) {
    public Question(String question) {
      this(question, null);
    }
  }

  public record Answer(
      String answer,
      List<String> sources,
      List<String> components,
      InvestoryRagClient.MarketData marketData,
      InvestoryRagClient.MarketStatus marketStatus,
      Context context) {
    public Answer(String answer, List<String> sources, List<String> components) {
      this(answer, sources, components, null, null, null);
    }
  }
}
