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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LongTermAssetCrudUiIT extends FastDatabaseTest {

  private static final Path ARTIFACT_DIRECTORY = Path.of("target", "ui-test-results");
  private static final long PORTFOLIO_ID = 1L;

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
  void otherAssetsCanBeCreatedEditedAndArchivedThroughTheUi() {
    runScenario(
        "other-assets-crud",
        page -> {
          var first =
              new BaseAsset(
                  "UI CRUD Other One",
                  "USD",
                  "2021-02-03",
                  "11111.11",
                  "12345.67",
                  "other first note");
          var second =
              new BaseAsset(
                  "UI CRUD Other Two",
                  "EUR",
                  "2022-03-04",
                  "21111.12",
                  "22345.68",
                  "other second note");

          long firstId = createOther(page, first);
          long secondId = createOther(page, second);
          assertOther(page, firstId, first, true);
          assertOther(page, secondId, second, true);
          assertActiveListContains(page, first.name(), second.name());

          var edited =
              new BaseAsset(
                  "UI CRUD Other One Edited",
                  first.currency(),
                  "2021-05-06",
                  "13111.13",
                  "14345.69",
                  "other first edited note");
          openDetail(page, firstId);
          fill(page, "#asset-name", edited.name());
          fill(page, "#asset-current-value", edited.currentValue());
          fill(page, "#asset-acquisition-date", edited.acquisitionDate());
          fill(page, "#asset-acquisition-value", edited.acquisitionValue());
          fill(page, "#asset-notes", edited.notes());
          submitFormContaining(page, "#asset-name", "Save changes");
          assertOther(page, firstId, edited, true);

          archiveAndAssert(page, secondId, second.name());
          assertActiveListContains(page, edited.name());
        });
  }

  @Test
  void bondsCanBeCreatedEditedAndArchivedThroughTheUi() {
    runScenario(
        "bonds-crud",
        page -> {
          var first =
              new BondAsset(
                  "UI CRUD Bond One",
                  "USD",
                  "2024-01-15",
                  "2031-06-30",
                  "25000.25",
                  "4.75",
                  "PAY_OUT",
                  "bond first note");
          var second =
              new BondAsset(
                  "UI CRUD Bond Two",
                  "EUR",
                  "2024-02-16",
                  "2032-07-31",
                  "35000.35",
                  "5.25",
                  "CAPITALIZE",
                  "bond second note");

          long firstId = createBond(page, first);
          long secondId = createBond(page, second);
          assertBond(page, firstId, first, first.value(), true);
          assertBond(page, secondId, second, second.value(), true);
          assertActiveListContains(page, first.name(), second.name());

          var edited =
              new BondAsset(
                  "UI CRUD Bond One Edited",
                  first.currency(),
                  "2024-03-17",
                  "2033-08-31",
                  "27500.75",
                  "6.35",
                  "CAPITALIZE",
                  "bond first edited note");
          openDetail(page, firstId);
          fill(page, "#bond-name", edited.name());
          fill(page, "#bond-value", edited.value());
          fill(page, "#bond-purchase-date", edited.acquisitionDate());
          fill(page, "#bond-maturity-date", edited.maturityDate());
          select(page, "#bond-interest-mode", edited.interestTreatment());
          fill(page, "#bond-rate", edited.annualRatePercent());
          fill(page, "#bond-notes", edited.notes());
          submitFormContaining(page, "#bond-name", "Save changes");
          assertBond(page, firstId, edited, first.value(), true);

          archiveAndAssert(page, secondId, second.name());
          assertActiveListContains(page, edited.name());
        });
  }

  @Test
  void cashReservesCanBeCreatedEditedAndArchivedThroughTheUi() {
    runScenario(
        "cash-reserves-crud",
        page -> {
          var first =
              new CashAsset(
                  "UI CRUD Cash One", "USD", "18000.18", "3.25", "cash first note");
          var second =
              new CashAsset(
                  "UI CRUD Cash Two", "EUR", "28000.28", "2.75", "cash second note");

          long firstId = createCash(page, first);
          long secondId = createCash(page, second);
          LocalDate firstAcquisitionDate = assetDate(firstId);
          LocalDate secondAcquisitionDate = assetDate(secondId);
          assertCash(page, firstId, first, firstAcquisitionDate, first.value(), true);
          assertCash(page, secondId, second, secondAcquisitionDate, second.value(), true);
          assertActiveListContains(page, first.name(), second.name());

          var edited =
              new CashAsset(
                  "UI CRUD Cash One Edited",
                  first.currency(),
                  "19500.95",
                  "4.15",
                  "cash first edited note");
          openDetail(page, firstId);
          fill(page, "#reserve-name", edited.name());
          fill(page, "#reserve-value", edited.value());
          fill(page, "#reserve-return", edited.annualReturnPercent());
          fill(page, "#reserve-notes", edited.notes());
          submitFormContaining(page, "#reserve-name", "Save changes");
          assertCash(page, firstId, edited, firstAcquisitionDate, first.value(), true);

          archiveAndAssert(page, secondId, second.name());
          assertActiveListContains(page, edited.name());
        });
  }

  @Test
  void depositsCanBeCreatedEditedAndArchivedThroughTheUi() {
    runScenario(
        "deposits-crud",
        page -> {
          var first =
              new DepositAsset(
                  "UI CRUD Deposit One",
                  "USD",
                  "2025-01-10",
                  "2028-01-10",
                  "15000.15",
                  "0.0415",
                  "0.17",
                  "PAY_OUT",
                  "deposit first note");
          var second =
              new DepositAsset(
                  "UI CRUD Deposit Two",
                  "EUR",
                  "2025-02-11",
                  "2029-02-11",
                  "25000.25",
                  "0.0525",
                  "0.18",
                  "CAPITALIZE",
                  "deposit second note");

          long firstId = createDeposit(page, first);
          long secondId = createDeposit(page, second);
          assertDeposit(page, firstId, first, first.value(), true);
          assertDeposit(page, secondId, second, second.value(), true);
          assertActiveListContains(page, first.name(), second.name());

          var edited =
              new DepositAsset(
                  "UI CRUD Deposit One Edited",
                  first.currency(),
                  "2025-03-12",
                  "2030-03-12",
                  "17500.75",
                  "0.0635",
                  "0.16",
                  "CAPITALIZE",
                  "deposit first edited note");
          openDetail(page, firstId);
          fill(page, "#asset-name", edited.name());
          fill(page, "#asset-current-value", edited.value());
          fill(page, "#asset-acquisition-date", edited.acquisitionDate());
          fill(page, "#asset-acquisition-value", edited.value());
          fill(page, "#asset-notes", edited.notes());
          submitFormContaining(page, "#asset-name", "Save changes");
          fill(page, "#deposit-maturity", edited.maturityDate());
          fill(page, "#deposit-rate", edited.annualInterestRate());
          fill(page, "#deposit-tax", edited.taxRate());
          select(page, "#deposit-interest-mode", edited.interestTreatment());
          submitFormContaining(page, "#deposit-maturity", "Save deposit details");
          assertDeposit(page, firstId, edited, edited.value(), true);

          archiveAndAssert(page, secondId, second.name());
          assertActiveListContains(page, edited.name());
        });
  }

  @Test
  void realEstateAndRentalContractsCanBeCreatedEditedDeletedAndArchivedThroughTheUi() {
    runScenario(
        "real-estate-rental-crud",
        page -> {
          var first =
              new PropertyAsset(
                  "UI CRUD Property One",
                  "USD",
                  "2020-04-05",
                  "210000.10",
                  "280000.20",
                  "3200.30",
                  "2026-01-01",
                  "2100.10",
                  "150.20",
                  "310.30",
                  "90.40",
                  "1200.50",
                  "800.60",
                  "3.50",
                  "property first note");
          var second =
              new PropertyAsset(
                  "UI CRUD Property Two",
                  "EUR",
                  "2021-05-06",
                  "310000.10",
                  "380000.20",
                  "4200.30",
                  "2026-02-01",
                  "3100.10",
                  "250.20",
                  "410.30",
                  "190.40",
                  "2200.50",
                  "1800.60",
                  "4.50",
                  "property second note");

          long firstId = createProperty(page, first);
          long secondId = createProperty(page, second);
          long firstContractId = contractId(firstId);
          long secondContractId = contractId(secondId);
          assertProperty(page, firstId, first, true);
          assertInitialContract(page, firstId, firstContractId, first);
          assertProperty(page, secondId, second, true);
          assertInitialContract(page, secondId, secondContractId, second);
          assertActiveListContains(page, first.name(), second.name());

          var editedContract =
              new ContractAsset(
                  "Alice Tenant",
                  "alice@example.test",
                  "+48 555 010 101",
                  first.effectiveFrom(),
                  "2027-12-31",
                  "2450.75",
                  "MONTHLY",
                  "225.25",
                  "MONTHLY",
                  "1200.00",
                  "ANNUAL",
                  "355.50",
                  "MONTHLY",
                  true,
                  "175.75",
                  "MONTHLY",
                  true,
                  "110.25",
                  "MONTHLY",
                  false,
                  "1450.50",
                  "ANNUAL",
                  false,
                  "950.25",
                  "ANNUAL",
                  true,
                  "3450.75",
                  "TENANT");
          editContract(page, firstId, firstContractId, editedContract);
          assertContract(page, firstId, firstContractId, editedContract);

          deleteContract(page, secondId, secondContractId);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM investory.long_term_asset_rental_contracts WHERE asset_id = ?",
                      Integer.class,
                      secondId))
              .isZero();

          var editedProperty =
              new PropertyAsset(
                  "UI CRUD Property One Edited",
                  first.currency(),
                  "2020-06-07",
                  first.acquisitionValue(),
                  "295000.95",
                  "3600.00",
                  first.effectiveFrom(),
                  first.monthlyRent(),
                  first.monthlyParking(),
                  first.monthlyAdministration(),
                  first.monthlyOtherCost(),
                  first.annualPropertyTax(),
                  first.annualInsurance(),
                  "5.25",
                  "property first edited note");
          editProperty(page, firstId, editedProperty);
          assertProperty(page, firstId, editedProperty, true);
          assertContract(page, firstId, firstContractId, editedContract);

          archiveAndAssert(page, secondId, second.name());
          assertActiveListContains(page, editedProperty.name());
        });
  }

  private long createOther(Page page, BaseAsset asset) {
    page.navigate(baseUrl() + "/long-term-assets/new?portfolioId=" + PORTFOLIO_ID);
    fill(page, "[name='name']", asset.name());
    select(page, "[name='currency']", asset.currency());
    fill(page, "[name='currentValue']", asset.currentValue());
    fill(page, "[name='acquisitionDate']", asset.acquisitionDate());
    fill(page, "[name='acquisitionValue']", asset.acquisitionValue());
    fill(page, "[name='notes']", asset.notes());
    clickButton(page, "Save");
    assertThat(page.url()).contains("/long-term-assets?portfolioId=1");
    return assetId(asset.name());
  }

  private long createBond(Page page, BondAsset asset) {
    page.navigate(baseUrl() + "/long-term-assets/new/bond?portfolioId=" + PORTFOLIO_ID);
    fill(page, "[name='name']", asset.name());
    select(page, "[name='currency']", asset.currency());
    fill(page, "[name='value']", asset.value());
    fill(page, "[name='acquisitionDate']", asset.acquisitionDate());
    fill(page, "[name='maturityDate']", asset.maturityDate());
    select(page, "[name='interestTreatment']", asset.interestTreatment());
    fill(page, "[name='annualRatePercent']", asset.annualRatePercent());
    fill(page, "[name='notes']", asset.notes());
    clickButton(page, "Save bond");
    return assetId(asset.name());
  }

  private long createCash(Page page, CashAsset asset) {
    page.navigate(baseUrl() + "/long-term-assets/new/cash-reserve?portfolioId=" + PORTFOLIO_ID);
    fill(page, "[name='name']", asset.name());
    select(page, "[name='currency']", asset.currency());
    fill(page, "[name='value']", asset.value());
    fill(page, "[name='annualReturnPercent']", asset.annualReturnPercent());
    fill(page, "[name='notes']", asset.notes());
    clickButton(page, "Save cash reserve");
    return assetId(asset.name());
  }

  private long createDeposit(Page page, DepositAsset asset) {
    page.navigate(baseUrl() + "/long-term-assets/new/deposit?portfolioId=" + PORTFOLIO_ID);
    fill(page, "[name='name']", asset.name());
    select(page, "[name='currency']", asset.currency());
    fill(page, "[name='value']", asset.value());
    fill(page, "[name='acquisitionDate']", asset.acquisitionDate());
    fill(page, "[name='maturityDate']", asset.maturityDate());
    fill(page, "[name='annualInterestRate']", asset.annualInterestRate());
    fill(page, "[name='taxRate']", asset.taxRate());
    select(page, "[name='interestTreatment']", asset.interestTreatment());
    fill(page, "[name='notes']", asset.notes());
    clickButton(page, "Save deposit");
    return assetId(asset.name());
  }

  private long createProperty(Page page, PropertyAsset asset) {
    page.navigate(baseUrl() + "/long-term-assets/new/real-estate?portfolioId=" + PORTFOLIO_ID);
    fill(page, "[name='name']", asset.name());
    select(page, "[name='currency']", asset.currency());
    fill(page, "[name='currentValue']", asset.currentValue());
    fill(page, "[name='taxBase']", asset.taxBase());
    fill(page, "[name='acquisitionDate']", asset.acquisitionDate());
    fill(page, "[name='acquisitionValue']", asset.acquisitionValue());
    fill(page, "[name='effectiveFrom']", asset.effectiveFrom());
    fill(page, "[name='monthlyRent']", asset.monthlyRent());
    fill(page, "[name='monthlyParkingIncome']", asset.monthlyParking());
    fill(page, "[name='monthlyAdministrationCost']", asset.monthlyAdministration());
    fill(page, "[name='monthlyOtherCost']", asset.monthlyOtherCost());
    fill(page, "[name='annualPropertyTax']", asset.annualPropertyTax());
    fill(page, "[name='annualInsurance']", asset.annualInsurance());
    fill(page, "[name='expectedAnnualGrowthRatePercent']", asset.growthPercent());
    fill(page, "[name='notes']", asset.notes());
    clickButton(page, "Save property");
    return assetId(asset.name());
  }

  private void assertOther(Page page, long id, BaseAsset asset, boolean active) {
    openDetail(page, id);
    assertInput(page, "#asset-name", asset.name());
    assertInput(page, "#asset-type", "Other");
    assertInput(page, "#asset-currency", asset.currency());
    assertInput(page, "#asset-current-value", asset.currentValue());
    assertInput(page, "#asset-acquisition-date", asset.acquisitionDate());
    assertInput(page, "#asset-acquisition-value", asset.acquisitionValue());
    assertInput(page, "#asset-notes", asset.notes());
    assertBaseDb(
        id,
        asset.name(),
        "OTHER",
        asset.currency(),
        asset.acquisitionDate(),
        asset.acquisitionValue(),
        asset.currentValue(),
        null,
        asset.notes(),
        active);
  }

  private void assertBond(
      Page page, long id, BondAsset asset, String acquisitionValue, boolean active) {
    openDetail(page, id);
    assertInput(page, "#bond-name", asset.name());
    assertInput(page, "#bond-currency", asset.currency());
    assertInput(page, "#bond-value", asset.value());
    assertInput(page, "#bond-purchase-date", asset.acquisitionDate());
    assertInput(page, "#bond-maturity-date", asset.maturityDate());
    assertInput(page, "#bond-interest-mode", asset.interestTreatment());
    assertInput(page, "#bond-rate", asset.annualRatePercent());
    assertInput(page, "#bond-notes", asset.notes());
    assertBaseDb(
        id,
        asset.name(),
        "BOND",
        asset.currency(),
        asset.acquisitionDate(),
        acquisitionValue,
        asset.value(),
        null,
        asset.notes(),
        active);
    Map<String, Object> details =
        jdbc.queryForMap(
            "SELECT maturity_date, interest_treatment, tax_rate, redemption_value "
                + "FROM investory.long_term_asset_bond_details WHERE asset_id = ?",
            id);
    assertDate(details.get("maturity_date"), asset.maturityDate());
    assertThat(details.get("interest_treatment")).isEqualTo(asset.interestTreatment());
    assertDecimal(details.get("tax_rate"), "0.19");
    assertDecimal(details.get("redemption_value"), acquisitionValue);
    Map<String, Object> rate =
        jdbc.queryForMap(
            "SELECT valid_from, valid_to, annual_interest_rate "
                + "FROM investory.long_term_asset_bond_rate_periods WHERE asset_id = ?",
            id);
    assertDate(rate.get("valid_from"), asset.acquisitionDate());
    assertDate(rate.get("valid_to"), asset.maturityDate());
    assertDecimal(rate.get("annual_interest_rate"), percentRate(asset.annualRatePercent()));
    assertCount("investory.long_term_asset_bond_details", "asset_id", id, 1);
    assertCount("investory.long_term_asset_bond_rate_periods", "asset_id", id, 1);
  }

  private void assertCash(
      Page page,
      long id,
      CashAsset asset,
      LocalDate acquisitionDate,
      String acquisitionValue,
      boolean active) {
    openDetail(page, id);
    assertInput(page, "#reserve-name", asset.name());
    assertInput(page, "#reserve-currency", asset.currency());
    assertInput(page, "#reserve-value", asset.value());
    assertInput(page, "#reserve-return", asset.annualReturnPercent());
    assertInput(page, "#reserve-notes", asset.notes());
    assertBaseDb(
        id,
        asset.name(),
        "CASH_RESERVE",
        asset.currency(),
        acquisitionDate.toString(),
        acquisitionValue,
        asset.value(),
        null,
        asset.notes(),
        active);
    Map<String, Object> rate =
        jdbc.queryForMap(
            "SELECT valid_from, valid_to, expected_annual_growth_rate "
                + "FROM investory.long_term_asset_valuation_periods WHERE asset_id = ?",
            id);
    assertDate(rate.get("valid_from"), acquisitionDate.toString());
    assertThat(rate.get("valid_to")).isNull();
    assertDecimal(rate.get("expected_annual_growth_rate"), percentRate(asset.annualReturnPercent()));
    assertCount("investory.long_term_asset_valuation_periods", "asset_id", id, 1);
  }

  private void assertDeposit(
      Page page, long id, DepositAsset asset, String acquisitionValue, boolean active) {
    openDetail(page, id);
    assertInput(page, "#asset-name", asset.name());
    assertInput(page, "#asset-type", "Deposit");
    assertInput(page, "#asset-currency", asset.currency());
    assertInput(page, "#asset-current-value", asset.value());
    assertInput(page, "#asset-acquisition-date", asset.acquisitionDate());
    assertInput(page, "#asset-acquisition-value", acquisitionValue);
    assertInput(page, "#asset-notes", asset.notes());
    assertInput(page, "#deposit-maturity", asset.maturityDate());
    assertNumberInput(page, "#deposit-rate", asset.annualInterestRate());
    assertNumberInput(page, "#deposit-tax", asset.taxRate());
    assertInput(page, "#deposit-interest-mode", asset.interestTreatment());
    assertBaseDb(
        id,
        asset.name(),
        "DEPOSIT",
        asset.currency(),
        asset.acquisitionDate(),
        acquisitionValue,
        asset.value(),
        null,
        asset.notes(),
        active);
    Map<String, Object> details =
        jdbc.queryForMap(
            "SELECT maturity_date, annual_interest_rate, tax_rate, interest_treatment "
                + "FROM investory.long_term_asset_deposit_details WHERE asset_id = ?",
            id);
    assertDate(details.get("maturity_date"), asset.maturityDate());
    assertDecimal(details.get("annual_interest_rate"), asset.annualInterestRate());
    assertDecimal(details.get("tax_rate"), asset.taxRate());
    assertThat(details.get("interest_treatment")).isEqualTo(asset.interestTreatment());
    assertCount("investory.long_term_asset_deposit_details", "asset_id", id, 1);
  }

  private void assertProperty(Page page, long id, PropertyAsset asset, boolean active) {
    openDetail(page, id);
    page.locator("#property-settings > summary").click();
    assertInput(page, "#property-name", asset.name());
    assertInput(page, "#property-currency", asset.currency());
    assertInput(page, "#property-value", asset.currentValue());
    assertInput(page, "#purchase-date", asset.acquisitionDate());
    assertInput(page, "#annual-tax-base", asset.taxBase());
    assertInput(page, "#property-notes", asset.notes());
    assertBaseDb(
        id,
        asset.name(),
        "REAL_ESTATE",
        asset.currency(),
        asset.acquisitionDate(),
        asset.acquisitionValue(),
        asset.currentValue(),
        asset.taxBase(),
        asset.notes(),
        active);
    Map<String, Object> growth =
        jdbc.queryForMap(
            "SELECT valid_from, expected_annual_growth_rate "
                + "FROM investory.long_term_asset_valuation_periods WHERE asset_id = ? "
                + "ORDER BY valid_from DESC LIMIT 1",
            id);
    assertDecimal(growth.get("expected_annual_growth_rate"), percentRate(asset.growthPercent()));
  }

  private void assertInitialContract(
      Page page, long assetId, long contractId, PropertyAsset asset) {
    var expected =
        new ContractAsset(
            null,
            null,
            null,
            asset.effectiveFrom(),
            null,
            asset.monthlyRent(),
            "MONTHLY",
            asset.monthlyParking(),
            "MONTHLY",
            null,
            "MONTHLY",
            asset.monthlyAdministration(),
            "MONTHLY",
            true,
            null,
            "MONTHLY",
            false,
            asset.monthlyOtherCost(),
            "MONTHLY",
            false,
            asset.annualPropertyTax(),
            "ANNUAL",
            false,
            asset.annualInsurance(),
            "ANNUAL",
            false,
            asset.taxBase(),
            "LANDLORD");
    assertContract(page, assetId, contractId, expected);
  }

  private void editContract(Page page, long assetId, long contractId, ContractAsset contract) {
    openDetail(page, assetId);
    Locator panel = page.locator("[data-contract-id='" + contractId + "']");
    panel.locator("[data-edit-contract]").click();
    String prefix = "#edit-" + contractId;
    fill(page, prefix + "-tenant-name", contract.tenantName());
    fill(page, prefix + "-tenant-email", contract.tenantEmail());
    fill(page, prefix + "-tenant-phone", contract.tenantPhone());
    fill(page, prefix + "-start-date", contract.startDate());
    fill(page, prefix + "-expected-end", contract.endDate());
    fill(page, prefix + "-rent", contract.rent());
    select(page, prefix + "-rent-frequency", contract.rentFrequency());
    fill(page, prefix + "-parking", contract.parking());
    select(page, prefix + "-parking-frequency", contract.parkingFrequency());
    fill(page, prefix + "-other-income", contract.otherIncome());
    select(page, prefix + "-other-income-frequency", contract.otherIncomeFrequency());
    fill(page, prefix + "-administration", contract.administration());
    select(page, prefix + "-administration-frequency", contract.administrationFrequency());
    select(page, prefix + "-administration-payer", Boolean.toString(contract.administrationTenant()));
    fill(page, prefix + "-utilities", contract.utilities());
    select(page, prefix + "-utilities-frequency", contract.utilitiesFrequency());
    select(page, prefix + "-utilities-payer", Boolean.toString(contract.utilitiesTenant()));
    fill(page, prefix + "-other-expense", contract.otherExpense());
    select(page, prefix + "-other-expense-frequency", contract.otherExpenseFrequency());
    select(page, prefix + "-other-expense-payer", Boolean.toString(contract.otherExpenseTenant()));
    fill(page, prefix + "-property-tax", contract.propertyTax());
    select(page, prefix + "-property-tax-frequency", contract.propertyTaxFrequency());
    select(page, prefix + "-property-tax-payer", Boolean.toString(contract.propertyTaxTenant()));
    fill(page, prefix + "-insurance", contract.insurance());
    select(page, prefix + "-insurance-frequency", contract.insuranceFrequency());
    select(page, prefix + "-insurance-payer", Boolean.toString(contract.insuranceTenant()));
    fill(page, prefix + "-monthly-tax-base", contract.monthlyTaxBase());
    select(page, prefix + "-tax-ownership", contract.taxOwnership());
    panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save changes")).click();
    assertThat(page.locator("[role='status']").textContent()).contains("Rental contract updated");
  }

  private void assertContract(Page page, long assetId, long contractId, ContractAsset contract) {
    openDetail(page, assetId);
    Locator panel = page.locator("[data-contract-id='" + contractId + "']");
    assertThat(panel.count()).isEqualTo(1);
    if (contract.tenantName() != null) assertThat(panel.textContent()).contains(contract.tenantName());
    panel.locator("[data-edit-contract]").click();
    String prefix = "#edit-" + contractId;
    assertInput(page, prefix + "-tenant-name", nullToEmpty(contract.tenantName()));
    assertInput(page, prefix + "-tenant-email", nullToEmpty(contract.tenantEmail()));
    assertInput(page, prefix + "-tenant-phone", nullToEmpty(contract.tenantPhone()));
    assertInput(page, prefix + "-start-date", contract.startDate());
    assertInput(page, prefix + "-expected-end", nullToEmpty(contract.endDate()));
    assertInput(page, prefix + "-rent", nullToEmpty(contract.rent()));
    assertInput(page, prefix + "-parking", nullToEmpty(contract.parking()));
    assertInput(page, prefix + "-other-income", nullToEmpty(contract.otherIncome()));
    assertInput(page, prefix + "-administration", nullToEmpty(contract.administration()));
    assertInput(page, prefix + "-utilities", nullToEmpty(contract.utilities()));
    assertInput(page, prefix + "-other-expense", nullToEmpty(contract.otherExpense()));
    assertInput(page, prefix + "-property-tax", nullToEmpty(contract.propertyTax()));
    assertInput(page, prefix + "-insurance", nullToEmpty(contract.insurance()));
    assertInput(page, prefix + "-monthly-tax-base", nullToEmpty(contract.monthlyTaxBase()));
    panel.locator("[data-cancel-edit]").click();

    Map<String, Object> header =
        jdbc.queryForMap(
            "SELECT start_date, end_date, tenant_name, tenant_email, tenant_phone, "
                + "monthly_tax_base, rental_tax_paid_by_tenant "
                + "FROM investory.long_term_asset_rental_contracts WHERE id = ? AND asset_id = ?",
            contractId,
            assetId);
    assertDate(header.get("start_date"), contract.startDate());
    assertDate(header.get("end_date"), contract.endDate());
    assertThat(header.get("tenant_name")).isEqualTo(contract.tenantName());
    assertThat(header.get("tenant_email")).isEqualTo(contract.tenantEmail());
    assertThat(header.get("tenant_phone")).isEqualTo(contract.tenantPhone());
    assertDecimal(header.get("monthly_tax_base"), contract.monthlyTaxBase());
    assertThat(header.get("rental_tax_paid_by_tenant"))
        .isEqualTo("TENANT".equals(contract.taxOwnership()));
    assertTerm(contractId, "RENT", contract.rent(), contract.rentFrequency(), false);
    assertTerm(contractId, "PARKING_RENT", contract.parking(), contract.parkingFrequency(), false);
    assertTerm(contractId, "OTHER_INCOME", contract.otherIncome(), contract.otherIncomeFrequency(), false);
    assertTerm(
        contractId,
        "ADMIN_FEE",
        contract.administration(),
        contract.administrationFrequency(),
        contract.administrationTenant());
    assertTerm(
        contractId,
        "UTILITIES",
        contract.utilities(),
        contract.utilitiesFrequency(),
        contract.utilitiesTenant());
    assertTerm(
        contractId,
        "OTHER_EXPENSE",
        contract.otherExpense(),
        contract.otherExpenseFrequency(),
        contract.otherExpenseTenant());
    assertTerm(
        contractId,
        "PROPERTY_TAX",
        contract.propertyTax(),
        contract.propertyTaxFrequency(),
        contract.propertyTaxTenant());
    assertTerm(
        contractId,
        "INSURANCE",
        contract.insurance(),
        contract.insuranceFrequency(),
        contract.insuranceTenant());
    int expectedTerms =
        (int)
            java.util.stream.Stream.of(
                    contract.rent(),
                    contract.parking(),
                    contract.otherIncome(),
                    contract.administration(),
                    contract.utilities(),
                    contract.otherExpense(),
                    contract.propertyTax(),
                    contract.insurance())
                .filter(value -> value != null && new BigDecimal(value).signum() != 0)
                .count();
    assertCount("investory.long_term_asset_rental_contract_terms", "contract_id", contractId, expectedTerms);
  }

  private void deleteContract(Page page, long assetId, long contractId) {
    openDetail(page, assetId);
    page.onDialog(dialog -> dialog.accept());
    page.locator("[data-contract-id='" + contractId + "'] [data-delete-contract] button").click();
    assertThat(page.locator("[data-contract-id='" + contractId + "']").count()).isZero();
    assertThat(page.locator("[role='status']").textContent()).contains("Rental contract deleted");
    assertCount("investory.long_term_asset_rental_contracts", "id", contractId, 0);
    assertCount("investory.long_term_asset_rental_contract_terms", "contract_id", contractId, 0);
  }

  private void editProperty(Page page, long id, PropertyAsset asset) {
    openDetail(page, id);
    page.locator("#property-settings > summary").click();
    fill(page, "#property-name", asset.name());
    fill(page, "#property-value", asset.currentValue());
    fill(page, "#purchase-date", asset.acquisitionDate());
    fill(page, "#annual-tax-base", asset.taxBase());
    fill(page, "#property-notes", asset.notes());
    submitFormContaining(page, "#property-name", "Save property");

    page.locator("#property-settings > summary").click();
    select(page, "#property-default-tax", "true");
    submitFormContaining(page, "#property-default-tax", "Save tax default");

    page.locator(".iv-property-advanced > summary").click();
    fill(page, "#growth-rate", asset.growthPercent());
    fill(page, "#growth-from", "2027-01-01");
    submitFormContaining(page, "#growth-rate", "Save growth assumption");
    assertThat(
            jdbc.queryForObject(
                "SELECT rental_tax_paid_by_tenant FROM investory.long_term_assets WHERE id = ?",
                Boolean.class,
                id))
        .isTrue();
  }

  private void assertTerm(
      long contractId, String type, String amount, String frequency, boolean paidByTenant) {
    if (amount == null || new BigDecimal(amount).signum() == 0) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM investory.long_term_asset_rental_contract_terms "
                      + "WHERE contract_id = ? AND cash_flow_type = ?",
                  Integer.class,
                  contractId,
                  type))
          .isZero();
      return;
    }
    Map<String, Object> term =
        jdbc.queryForMap(
            "SELECT amount, frequency, paid_by_tenant "
                + "FROM investory.long_term_asset_rental_contract_terms "
                + "WHERE contract_id = ? AND cash_flow_type = ?",
            contractId,
            type);
    assertDecimal(term.get("amount"), amount);
    assertThat(term.get("frequency")).isEqualTo(frequency);
    assertThat(term.get("paid_by_tenant")).isEqualTo(paidByTenant);
  }

  private void assertBaseDb(
      long id,
      String name,
      String type,
      String currency,
      String acquisitionDate,
      String acquisitionValue,
      String currentValue,
      String taxBase,
      String notes,
      boolean active) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT portfolio_id, name, asset_type, currency, acquisition_date, acquisition_value, "
                + "current_value, tax_base, active, notes FROM investory.long_term_assets WHERE id = ?",
            id);
    assertThat(row.get("portfolio_id")).isEqualTo(PORTFOLIO_ID);
    assertThat(row.get("name")).isEqualTo(name);
    assertThat(row.get("asset_type")).isEqualTo(type);
    assertThat(row.get("currency")).isEqualTo(currency);
    assertDate(row.get("acquisition_date"), acquisitionDate);
    assertDecimal(row.get("acquisition_value"), acquisitionValue);
    assertDecimal(row.get("current_value"), currentValue);
    assertDecimal(row.get("tax_base"), taxBase);
    assertThat(row.get("active")).isEqualTo(active);
    assertThat(row.get("notes")).isEqualTo(notes);
  }

  private void archiveAndAssert(Page page, long id, String name) {
    openDetail(page, id);
    revealPropertySettingsIfNeeded(page, "Archive");
    clickButton(page, "Archive");
    assertThat(page.url()).contains("/long-term-assets?portfolioId=1");
    assertThat(exactLink(page, name).count()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM investory.long_term_assets WHERE id = ?", Boolean.class, id))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT archived_at FROM investory.long_term_assets WHERE id = ?",
                LocalDate.class,
                id))
        .isNotNull();
    openDetail(page, id);
    revealPropertySettingsIfNeeded(page, "Reactivate");
    assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reactivate")).isVisible())
        .isTrue();
  }

  private void revealPropertySettingsIfNeeded(Page page, String action) {
    Locator actionButton =
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName(action).setExact(true));
    if (actionButton.count() > 0 && !actionButton.isVisible())
      page.locator("#property-settings > summary").click();
  }

  private void assertActiveListContains(Page page, String... names) {
    page.navigate(baseUrl() + "/long-term-assets?portfolioId=" + PORTFOLIO_ID);
    for (String name : names) assertThat(exactLink(page, name).count()).isEqualTo(1);
  }

  private Locator exactLink(Page page, String name) {
    return page.locator("a").filter(new Locator.FilterOptions().setHasText(name));
  }

  private void openDetail(Page page, long id) {
    page.navigate(baseUrl() + "/long-term-assets/" + id + "?portfolioId=" + PORTFOLIO_ID);
    assertThat(page.url()).contains("/long-term-assets/" + id);
    assertThat(page.locator("main").isVisible()).isTrue();
  }

  private void submitFormContaining(Page page, String fieldSelector, String buttonName) {
    page.locator(fieldSelector)
        .locator("xpath=ancestor::form")
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(buttonName).setExact(true))
        .click();
  }

  private void clickButton(Page page, String name) {
    page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true))
        .click();
  }

  private static void fill(Page page, String selector, String value) {
    if (value != null) page.locator(selector).fill(value);
  }

  private static void select(Page page, String selector, String value) {
    page.locator(selector).selectOption(value);
  }

  private static void assertInput(Page page, String selector, String expected) {
    Locator input = page.locator(selector);
    if ("number".equals(input.getAttribute("type")) && !expected.isEmpty()) {
      assertThat(new BigDecimal(input.inputValue())).as(selector).isEqualByComparingTo(expected);
    } else {
      assertThat(input.inputValue()).as(selector).isEqualTo(expected);
    }
  }

  private static void assertNumberInput(Page page, String selector, String expected) {
    assertThat(new BigDecimal(page.locator(selector).inputValue()))
        .as(selector)
        .isEqualByComparingTo(expected);
  }

  private long assetId(String name) {
    return jdbc.queryForObject(
        "SELECT id FROM investory.long_term_assets WHERE portfolio_id = ? AND name = ?",
        Long.class,
        PORTFOLIO_ID,
        name);
  }

  private long contractId(long assetId) {
    return jdbc.queryForObject(
        "SELECT id FROM investory.long_term_asset_rental_contracts WHERE asset_id = ?",
        Long.class,
        assetId);
  }

  private LocalDate assetDate(long assetId) {
    return jdbc.queryForObject(
        "SELECT acquisition_date FROM investory.long_term_assets WHERE id = ?",
        LocalDate.class,
        assetId);
  }

  private void assertCount(String table, String column, long id, int expected) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id))
        .isEqualTo(expected);
  }

  private static void assertDecimal(Object actual, String expected) {
    if (expected == null) {
      assertThat(actual).isNull();
    } else {
      assertThat((BigDecimal) actual).isEqualByComparingTo(expected);
    }
  }

  private static void assertDate(Object actual, String expected) {
    if (expected == null) {
      assertThat(actual).isNull();
      return;
    }
    LocalDate actualDate =
        actual instanceof java.sql.Date sqlDate
            ? sqlDate.toLocalDate()
            : actual instanceof LocalDate localDate
                ? localDate
                : LocalDate.parse(actual.toString());
    assertThat(actualDate).isEqualTo(LocalDate.parse(expected));
  }

  private static String percentRate(String percent) {
    return new BigDecimal(percent).movePointLeft(2).toPlainString();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private void runScenario(String name, Scenario scenario) {
    List<String> browserFailures = new ArrayList<>();
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(1440, 1000));
    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
    Page page = context.newPage();
    page.onPageError(error -> browserFailures.add("page error: " + error));
    page.onConsoleMessage(
        message -> {
          if ("error".equals(message.type()))
            browserFailures.add("console error: " + message.text());
        });
    page.onRequestFailed(
        request -> {
          if (request.url().startsWith(baseUrl())
              && (request.failure() == null || !request.failure().contains("ERR_ABORTED")))
            browserFailures.add("request failed: " + request.method() + " " + request.url());
        });

    boolean passed = false;
    try {
      scenario.run(page);
      assertThat(browserFailures).isEmpty();
      passed = true;
    } catch (AssertionError | RuntimeException failure) {
      saveFailureArtifacts(name, page, context);
      throw failure;
    } finally {
      if (passed) context.tracing().stop();
      context.close();
    }
  }

  private static void saveFailureArtifacts(String name, Page page, BrowserContext context) {
    try {
      Files.createDirectories(ARTIFACT_DIRECTORY);
      String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "-");
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(ARTIFACT_DIRECTORY.resolve(safeName + ".png"))
              .setFullPage(true));
      Files.writeString(ARTIFACT_DIRECTORY.resolve(safeName + ".html"), page.content());
      context
          .tracing()
          .stop(
              new Tracing.StopOptions()
                  .setPath(ARTIFACT_DIRECTORY.resolve(safeName + "-trace.zip")));
    } catch (IOException | RuntimeException ignored) {
      // Preserve the original assertion failure.
    }
  }

  @FunctionalInterface
  private interface Scenario {
    void run(Page page);
  }

  private record BaseAsset(
      String name,
      String currency,
      String acquisitionDate,
      String acquisitionValue,
      String currentValue,
      String notes) {}

  private record BondAsset(
      String name,
      String currency,
      String acquisitionDate,
      String maturityDate,
      String value,
      String annualRatePercent,
      String interestTreatment,
      String notes) {}

  private record CashAsset(
      String name, String currency, String value, String annualReturnPercent, String notes) {}

  private record DepositAsset(
      String name,
      String currency,
      String acquisitionDate,
      String maturityDate,
      String value,
      String annualInterestRate,
      String taxRate,
      String interestTreatment,
      String notes) {}

  private record PropertyAsset(
      String name,
      String currency,
      String acquisitionDate,
      String acquisitionValue,
      String currentValue,
      String taxBase,
      String effectiveFrom,
      String monthlyRent,
      String monthlyParking,
      String monthlyAdministration,
      String monthlyOtherCost,
      String annualPropertyTax,
      String annualInsurance,
      String growthPercent,
      String notes) {}

  private record ContractAsset(
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      String startDate,
      String endDate,
      String rent,
      String rentFrequency,
      String parking,
      String parkingFrequency,
      String otherIncome,
      String otherIncomeFrequency,
      String administration,
      String administrationFrequency,
      boolean administrationTenant,
      String utilities,
      String utilitiesFrequency,
      boolean utilitiesTenant,
      String otherExpense,
      String otherExpenseFrequency,
      boolean otherExpenseTenant,
      String propertyTax,
      String propertyTaxFrequency,
      boolean propertyTaxTenant,
      String insurance,
      String insuranceFrequency,
      boolean insuranceTenant,
      String monthlyTaxBase,
      String taxOwnership) {}
}
