package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(
    scripts = "/ui/ui-page-smoke-fixture.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class PlanSimulationCrudUiIT extends FastDatabaseTest {

  private static final long PORTFOLIO_ID = 1L;
  private static final long SOURCE_PLAN_ID = 9201L;
  private static final Path ARTIFACT_DIRECTORY = Path.of("target", "ui-test-results");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;

  private Playwright playwright;
  private Browser browser;

  @BeforeAll
  void launchBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeBrowser() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Test
  void plansAndEventsCanBeCreatedEditedSimulatedAndRemovedThroughTheUi() {
    runScenario(
        page -> {
          PlanData first =
              new PlanData(
                  "UI Simulation Plan One",
                  "2024",
                  "40",
                  "63",
                  "88",
                  "4321.09",
                  "8765.43",
                  "2.3",
                  "1.1",
                  "1.7",
                  "MANUAL",
                  "15432.10",
                  "MANUAL",
                  "8765.40",
                  "120000.50",
                  "18000.75",
                  "36000.25",
                  "67",
                  "4.4",
                  "7.6",
                  "2.5",
                  "6.5",
                  "60",
                  true);
          PlanData second =
              new PlanData(
                  "UI Simulation Plan Two",
                  "2025",
                  "43",
                  "66",
                  "91",
                  "5432.10",
                  "9876.54",
                  "3.1",
                  "0.8",
                  "2.0",
                  "SOURCE",
                  "0",
                  "MANUAL",
                  "7654.30",
                  "110000.25",
                  "15000.50",
                  "42000.75",
                  "68",
                  "3.9",
                  "6.8",
                  "3.5",
                  "5.5",
                  "45",
                  false);

          long firstId = createAsNewPlan(page, SOURCE_PLAN_ID, first);
          assertEditorAndDatabase(page, firstId, first, 1, false);
          long secondId = createAsNewPlan(page, firstId, second);
          assertEditorAndDatabase(page, secondId, second, 1, false);

          EventData firstEvent =
              new EventData("2032", "UI kitchen renovation", "45678.90", "ONE_OFF_EXPENSE");
          EventData secondEvent =
              new EventData("2035", "UI inheritance", "98765.40", "ONE_OFF_INCOME");
          long firstEventId = addEvent(page, firstId, firstEvent);
          long secondEventId = addEvent(page, secondId, secondEvent);
          assertEvent(page, firstId, firstEvent);
          assertEvent(page, secondId, secondEvent);

          PlanData edited =
              new PlanData(
                  "UI Simulation Plan One Edited",
                  "2023",
                  "39",
                  "64",
                  "90",
                  "4567.89",
                  "11234.56",
                  "2.7",
                  "1.3",
                  "1.9",
                  "MANUAL",
                  "16543.21",
                  "SOURCE",
                  "0",
                  "135000.75",
                  "22000.25",
                  "39000.50",
                  "69",
                  "4.8",
                  "8.2",
                  "1.7",
                  "7.2",
                  "70",
                  false);
          editPlan(page, firstId, edited);
          assertEditorAndDatabase(page, firstId, edited, 3, false);
          assertEvent(page, firstId, firstEvent);
          assertCurrentRevisionEvent(firstId, firstEvent);
          assertSimulation(page, firstId, edited);

          removeEvent(page, secondId, secondEventId, secondEvent);
          deletePlan(page, secondId, second.name());
          assertEditorAndDatabase(page, secondId, second, 3, true);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM investory.simulation_plan_revision_events e "
                          + "JOIN investory.simulation_plans p ON p.current_revision_id = e.revision_id "
                          + "WHERE p.id = ?",
                      Integer.class,
                      secondId))
              .isZero();
        });
  }

  private long createAsNewPlan(Page page, long sourcePlanId, PlanData plan) {
    openEditor(page, sourcePlanId);
    fillPlan(page, plan);
    page.locator(".iv-simulation-editor__plan-management > summary").click();
    page.locator("#plan-name").fill(plan.name());
    clickButton(page, "Save as new plan");
    assertThat(page.url()).contains("/simulation?").contains("planId=");
    return idByName(plan.name());
  }

  private void editPlan(Page page, long planId, PlanData plan) {
    openEditor(page, planId);
    page.locator(".iv-simulation-editor__plan-management > summary").click();
    page.locator("#plan-name").fill(plan.name());
    fillPlan(page, plan);
    clickButton(page, "Save plan");
    assertThat(page.url()).contains("/simulation?").contains("planId=" + planId);
  }

  private void fillPlan(Page page, PlanData plan) {
    fill(page, "#plan-start-year", plan.startYear());
    fill(page, "#age-at-plan-start", plan.ageAtPlanStart());
    fill(page, "#retirement-age", plan.retirementAge());
    fill(page, "#end-age", plan.endAge());
    fill(page, "#monthly-living-costs", plan.monthlyLivingCosts());
    fill(page, "#discretionary-expenses", plan.discretionaryExpenses());
    fill(page, "#inflation", plan.inflation());
    fill(page, "#spending-growth", plan.spendingGrowth());
    fill(page, "#rental-growth", plan.rentalGrowth());
    page.locator("#rental-income-mode").selectOption(plan.rentalIncomeMode());
    fill(page, "#manual-rental-income", plan.manualRentalIncome());
    page.locator("#bond-cash-income-mode").selectOption(plan.bondIncomeMode());
    fill(page, "#manual-bond-cash-income", plan.manualBondIncome());
    fill(page, "#employment-income", plan.employmentIncome());
    fill(page, "#pre-retirement-contribution", plan.preRetirementContribution());
    fill(page, "#annual-pension", plan.annualPension());
    fill(page, "#pension-start-age", plan.pensionStartAge());
    fill(page, "#fixed-income-return", plan.fixedIncomeReturn());
    fill(page, "#equity-return", plan.equityReturn());
    fill(page, "#safe-reserve-years", plan.safeReserveYears());
    fill(page, "#equity-harvest-threshold", plan.equityHarvestThreshold());
    fill(page, "#equity-harvest-share", plan.equityHarvestShare());
    page.locator("#emergency-equity").setChecked(plan.emergencyEquity());
  }

  private long addEvent(Page page, long planId, EventData event) {
    openEditor(page, planId);
    page.getByText("+ Add event", new Page.GetByTextOptions().setExact(true)).click();
    Locator form = page.locator(".iv-simulation-editor__event-form");
    form.locator("input[name='year']").fill(event.year());
    form.locator("input[name='name']").fill(event.name());
    form.locator("input[name='amount']").fill(event.amount());
    form.locator("select[name='type']").selectOption(event.type());
    form.getByRole(AriaRole.BUTTON).click();
    assertThat(page.url()).contains("/simulation/plan/edit").contains("planId=" + planId);
    return jdbc.queryForObject(
        "SELECT e.id FROM investory.simulation_plan_revision_events e "
            + "JOIN investory.simulation_plans p ON p.current_revision_id = e.revision_id "
            + "WHERE p.id = ? AND e.name = ?",
        Long.class,
        planId,
        event.name());
  }

  private void removeEvent(Page page, long planId, long eventId, EventData event) {
    openEditor(page, planId);
    Locator row = eventRow(page, event.name());
    row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Remove")).click();
    assertThat(eventRow(page, event.name()).count()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.simulation_plan_revision_events e "
                    + "JOIN investory.simulation_plans p ON p.current_revision_id = e.revision_id "
                    + "WHERE p.id = ? AND e.name = ?",
                Integer.class,
                planId,
                event.name()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.simulation_plan_revision_events WHERE id = ?",
                Integer.class,
                eventId))
        .isOne();
  }

  private void deletePlan(Page page, long planId, String name) {
    openEditor(page, planId);
    page.locator(".iv-simulation-editor__plan-management > summary").click();
    clickButton(page, "Delete plan");
    assertThat(page.url()).contains("/simulation/plan/edit");
    assertThat(
            page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("Active plan"))
                .locator("option")
                .allTextContents())
        .doesNotContain(name);
  }

  private void assertEditorAndDatabase(
      Page page, long planId, PlanData plan, int revisionNumber, boolean archived) {
    if (!archived) {
      openEditor(page, planId);
      page.locator(".iv-simulation-editor__plan-management > summary").click();
      assertThat(page.locator("#plan-name").inputValue()).isEqualTo(plan.name());
      assertInput(page, "#plan-start-year", plan.startYear());
      assertInput(page, "#age-at-plan-start", plan.ageAtPlanStart());
      assertInput(page, "#retirement-age", plan.retirementAge());
      assertInput(page, "#end-age", plan.endAge());
      assertInput(page, "#monthly-living-costs", plan.monthlyLivingCosts());
      assertInput(page, "#discretionary-expenses", plan.discretionaryExpenses());
      assertInput(page, "#inflation", plan.inflation());
      assertInput(page, "#spending-growth", plan.spendingGrowth());
      assertInput(page, "#rental-growth", plan.rentalGrowth());
      assertThat(page.locator("#rental-income-mode").inputValue())
          .isEqualTo(plan.rentalIncomeMode());
      assertInput(page, "#manual-rental-income", plan.manualRentalIncome());
      assertThat(page.locator("#bond-cash-income-mode").inputValue())
          .isEqualTo(plan.bondIncomeMode());
      assertInput(page, "#manual-bond-cash-income", plan.manualBondIncome());
      assertInput(page, "#employment-income", plan.employmentIncome());
      assertInput(page, "#pre-retirement-contribution", plan.preRetirementContribution());
      assertInput(page, "#annual-pension", plan.annualPension());
      assertInput(page, "#pension-start-age", plan.pensionStartAge());
      assertInput(page, "#fixed-income-return", plan.fixedIncomeReturn());
      assertInput(page, "#equity-return", plan.equityReturn());
      assertInput(page, "#safe-reserve-years", plan.safeReserveYears());
      assertInput(page, "#equity-harvest-threshold", plan.equityHarvestThreshold());
      assertInput(page, "#equity-harvest-share", plan.equityHarvestShare());
      assertThat(page.locator("#emergency-equity").isChecked()).isEqualTo(plan.emergencyEquity());
    }

    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM investory.simulation_plans WHERE id = ?", planId);
    assertPlanRow(row, plan);
    assertThat(row.get("portfolio_id")).isEqualTo(PORTFOLIO_ID);
    assertThat(row.get("archived")).isEqualTo(archived);
    assertThat(row.get("created_at")).isNotNull();
    assertThat(row.get("updated_at")).isNotNull();
    long revisionId = ((Number) row.get("current_revision_id")).longValue();
    Map<String, Object> revision =
        jdbc.queryForMap(
            "SELECT * FROM investory.simulation_plan_revisions WHERE id = ?", revisionId);
    assertPlanRow(revision, plan);
    assertThat(revision.get("simulation_plan_id")).isEqualTo(planId);
    assertThat(revision.get("revision_number")).isEqualTo(revisionNumber);
    assertThat(revision.get("baseline_as_of_year")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.simulation_plan_revisions WHERE simulation_plan_id = ?",
                Integer.class,
                planId))
        .isEqualTo(revisionNumber);
  }

  private void assertPlanRow(Map<String, Object> row, PlanData plan) {
    if (row.containsKey("name")) assertThat(row.get("name")).isEqualTo(plan.name());
    assertThat(row.get("current_age")).isEqualTo(Integer.valueOf(plan.ageAtPlanStart()));
    assertThat(row.get("start_year")).isEqualTo(Integer.valueOf(plan.startYear()));
    assertThat(row.get("end_age")).isEqualTo(Integer.valueOf(plan.endAge()));
    assertThat(row.get("retirement_age")).isEqualTo(Integer.valueOf(plan.retirementAge()));
    assertCanonical(row.get("annual_employment_income"), plan.employmentIncome());
    assertCanonical(
        row.get("annual_pre_retirement_contribution"), plan.preRetirementContribution());
    assertCanonicalMonthlyAnnual(row.get("annual_living_expenses"), plan.monthlyLivingCosts());
    assertCanonical(row.get("annual_discretionary_expenses"), plan.discretionaryExpenses());
    assertRate(row.get("inflation_rate"), plan.inflation());
    assertRate(row.get("rental_income_growth_rate"), plan.rentalGrowth());
    assertRate(row.get("spending_growth_rate"), plan.spendingGrowth());
    assertThat(row.get("funding_strategy")).isEqualTo("SIMPLE_WATERFALL");
    assertDecimal(row.get("safe_reserve_years"), plan.safeReserveYears());
    assertRate(row.get("equity_harvest_minimum_return_rate"), plan.equityHarvestThreshold());
    assertRate(row.get("equity_gain_harvest_rate"), plan.equityHarvestShare());
    assertThat(row.get("allow_emergency_equity_withdrawal")).isEqualTo(plan.emergencyEquity());
    assertRate(row.get("cash_return_rate"), "0");
    assertRate(row.get("fixed_income_return_rate"), plan.fixedIncomeReturn());
    assertRate(row.get("equity_return_rate"), plan.equityReturn());
    assertRate(row.get("real_estate_return_rate"), "4");
    assertRate(row.get("other_return_rate"), "0");
    assertThat(row.get("pension_start_age")).isEqualTo(Integer.valueOf(plan.pensionStartAge()));
    assertCanonical(row.get("annual_pension"), plan.annualPension());
    assertRate(row.get("capital_gain_tax_rate"), "0");
    assertThat(row.get("funding_order")).isEqualTo("CASH,BONDS,STOCKS");
    assertThat(row.get("expense_profile")).isEqualTo("");
    assertThat(row.get("rental_income_mode")).isEqualTo(plan.rentalIncomeMode());
    assertNullableCanonical(row.get("manual_rental_income"), plan.manualRentalIncome());
    assertThat(row.get("bond_cash_income_mode")).isEqualTo(plan.bondIncomeMode());
    assertNullableCanonical(row.get("manual_bond_cash_income"), plan.manualBondIncome());
  }

  private void assertEvent(Page page, long planId, EventData event) {
    openEditor(page, planId);
    Locator row = eventRow(page, event.name());
    assertThat(row.count()).isEqualTo(1);
    assertThat(row.textContent()).contains(event.year(), event.name());
    Map<String, Object> db =
        jdbc.queryForMap(
            "SELECT e.* FROM investory.simulation_plan_revision_events e "
                + "JOIN investory.simulation_plans p ON p.current_revision_id = e.revision_id "
                + "WHERE p.id = ? AND e.name = ?",
            planId,
            event.name());
    assertThat(db.get("event_year")).isEqualTo(Integer.valueOf(event.year()));
    assertThat(db.get("name")).isEqualTo(event.name());
    assertCanonical(db.get("amount"), event.amount());
    assertThat(db.get("event_type")).isEqualTo(event.type());
    assertThat(db.get("notes")).isEqualTo("");
    assertThat(db.get("created_at")).isNotNull();
  }

  private void assertCurrentRevisionEvent(long planId, EventData event) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT e.* FROM investory.simulation_plan_revision_events e "
                + "JOIN investory.simulation_plans p ON p.current_revision_id = e.revision_id "
                + "WHERE p.id = ? AND e.name = ?",
            planId,
            event.name());
    assertThat(row.get("event_year")).isEqualTo(Integer.valueOf(event.year()));
    assertCanonical(row.get("amount"), event.amount());
    assertThat(row.get("event_type")).isEqualTo(event.type());
  }

  private void assertSimulation(Page page, long planId, PlanData plan) {
    page.navigate(baseUrl() + "/simulation?portfolioId=1&planId=" + planId);
    assertThat(page.title()).contains("Retirement simulation");
    assertThat(page.locator("#simulation-assumptions-form input[name='planId']").inputValue())
        .isEqualTo(String.valueOf(planId));
    assertThat(
            page.locator("#simulation-assumptions-form input[name='selectedScenario']")
                .inputValue())
        .isEqualTo("BASE");
    int firstProjectedAge =
        Integer.parseInt(plan.ageAtPlanStart())
            + Year.now().getValue()
            - Integer.parseInt(plan.startYear())
            + 1;
    assertThat(page.locator(".iv-planning-summary").textContent())
        .contains("Planning horizon", firstProjectedAge + "–" + plan.endAge());
    assertThat(page.locator("#plan-year-selector option").count()).isGreaterThan(1);
    assertThat(page.locator(".iv-simulation-projection-row").count()).isGreaterThan(1);

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Conservative")).click();
    assertThat(page.url()).contains("selectedScenario=CONSERVATIVE");
    assertThat(
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Conservative"))
                .getAttribute("aria-current"))
        .isEqualTo("page");

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Custom")).click();
    Locator custom = page.locator(".iv-custom-scenario");
    custom.locator("input[name='customInflationDelta']").fill("0.7");
    custom.locator("input[name='customRentalGrowthDelta']").fill("-0.4");
    custom.locator("input[name='customBondReturnDelta']").fill("-0.8");
    custom.locator("input[name='customEquityReturnDelta']").fill("1.2");
    custom.locator("input[name='customSpendingGrowthDelta']").fill("0.5");
    custom.getByRole(AriaRole.BUTTON).click();
    assertThat(page.url())
        .contains("selectedScenario=CUSTOM")
        .contains("customInflationDelta=0.7")
        .contains("customEquityReturnDelta=1.2");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Reset")).click();
    custom = page.locator(".iv-custom-scenario");
    assertThat(custom.locator("input[name='customInflationDelta']").inputValue()).isEqualTo("0");
    assertThat(custom.locator("input[name='customEquityReturnDelta']").inputValue()).isEqualTo("0");
  }

  private void openEditor(Page page, long planId) {
    page.navigate(
        baseUrl() + "/simulation/plan/edit?portfolioId=" + PORTFOLIO_ID + "&planId=" + planId);
    assertThat(page.url()).contains("/simulation/plan/edit").contains("planId=" + planId);
    assertThat(page.title()).contains("Edit plan");
  }

  private Locator eventRow(Page page, String name) {
    return page.locator(".iv-simulation-editor__event-row")
        .filter(new Locator.FilterOptions().setHasText(name));
  }

  private long idByName(String name) {
    return jdbc.queryForObject(
        "SELECT id FROM investory.simulation_plans WHERE portfolio_id = ? AND name = ?",
        Long.class,
        PORTFOLIO_ID,
        name);
  }

  private void fill(Page page, String selector, String value) {
    page.locator(selector).fill(value == null ? "" : value);
  }

  private void clickButton(Page page, String name) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true))
        .click();
  }

  private void assertInput(Page page, String selector, String expected) {
    String actual = page.locator(selector).inputValue();
    if (expected == null || expected.isBlank()) assertThat(actual).isBlank();
    else assertThat(new BigDecimal(actual)).isEqualByComparingTo(expected);
  }

  private void assertRate(Object actual, String percent) {
    assertDecimal(actual, new BigDecimal(percent).movePointLeft(2).toPlainString());
  }

  private void assertNullableCanonical(Object actual, String expected) {
    if (expected == null || expected.isBlank()) assertThat(actual).isNull();
    else assertCanonical(actual, expected);
  }

  private void assertCanonical(Object actual, String displayed) {
    BigDecimal canonical = new BigDecimal(displayed).divide(usdPln(), 8, RoundingMode.HALF_UP);
    assertDecimal(actual, canonical.toPlainString());
  }

  private void assertCanonicalMonthlyAnnual(Object actual, String displayedMonthly) {
    BigDecimal canonicalMonthly =
        new BigDecimal(displayedMonthly).divide(usdPln(), 8, RoundingMode.HALF_UP);
    assertDecimal(actual, canonicalMonthly.multiply(BigDecimal.valueOf(12)).toPlainString());
  }

  private BigDecimal usdPln() {
    return jdbc.queryForObject(
        "SELECT rate FROM investory.exchange_rates "
            + "WHERE base = 'USD' AND to_currency = 'PLN' AND rate_date <= current_date "
            + "ORDER BY rate_date DESC LIMIT 1",
        BigDecimal.class);
  }

  private void assertDecimal(Object actual, String expected) {
    assertThat(actual).isInstanceOf(BigDecimal.class);
    assertThat((BigDecimal) actual).isEqualByComparingTo(expected);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private BrowserContext authenticatedContext() {
    return browser.newContext(
        new Browser.NewContextOptions()
            .setHttpCredentials("admin", "change-me-admin")
            .setViewportSize(1440, 1000));
  }

  private void runScenario(Scenario scenario) {
    var failures = new ArrayList<String>();
    try (BrowserContext context = authenticatedContext()) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      Page page = context.newPage();
      page.onPageError(error -> failures.add("page error: " + error));
      page.onConsoleMessage(
          message -> {
            if ("error".equals(message.type())) failures.add("console error: " + message.text());
          });
      page.onRequestFailed(
          request -> {
            String failure = request.failure();
            if (request.url().startsWith(baseUrl())
                && (failure == null || !failure.contains("ERR_ABORTED")))
              failures.add("request failed: " + request.method() + " " + request.url());
          });
      try {
        scenario.run(page);
        assertThat(failures).isEmpty();
        context.tracing().stop();
      } catch (AssertionError | RuntimeException failure) {
        saveFailureArtifacts(page, context);
        throw failure;
      }
    }
  }

  private void saveFailureArtifacts(Page page, BrowserContext context) {
    try {
      Files.createDirectories(ARTIFACT_DIRECTORY);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(ARTIFACT_DIRECTORY.resolve("plan-simulation-crud.png"))
              .setFullPage(true));
      Files.writeString(ARTIFACT_DIRECTORY.resolve("plan-simulation-crud.html"), page.content());
      context
          .tracing()
          .stop(
              new Tracing.StopOptions()
                  .setPath(ARTIFACT_DIRECTORY.resolve("plan-simulation-crud-trace.zip")));
    } catch (IOException | RuntimeException ignored) {
      // Preserve the original test failure.
    }
  }

  @FunctionalInterface
  private interface Scenario {
    void run(Page page);
  }

  private record EventData(String year, String name, String amount, String type) {}

  private record PlanData(
      String name,
      String startYear,
      String ageAtPlanStart,
      String retirementAge,
      String endAge,
      String monthlyLivingCosts,
      String discretionaryExpenses,
      String inflation,
      String spendingGrowth,
      String rentalGrowth,
      String rentalIncomeMode,
      String manualRentalIncome,
      String bondIncomeMode,
      String manualBondIncome,
      String employmentIncome,
      String preRetirementContribution,
      String annualPension,
      String pensionStartAge,
      String fixedIncomeReturn,
      String equityReturn,
      String safeReserveYears,
      String equityHarvestThreshold,
      String equityHarvestShare,
      boolean emergencyEquity) {}
}
