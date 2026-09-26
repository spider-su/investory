package com.smartbox.investory.ryczalt.application.onboarding;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RyczaltOnboardingService {
  private final JdbcTemplate jdbc;
  private final CompanyLookupClient lookupClient;

  public RyczaltOnboardingService(JdbcTemplate jdbc, CompanyLookupClient lookupClient) {
    this.jdbc = jdbc;
    this.lookupClient = lookupClient;
  }

  @Transactional
  public RyczaltOnboarding get(long profileId) {
    ensureRow(profileId);
    return read(profileId);
  }

  @Transactional
  public CompanyLookupResult lookupCompany(long profileId, String rawNip) {
    requireProfile(profileId);
    if (!NipValidator.isValid(rawNip)) throw badRequest("nip_invalid");
    CompanyLookupResult result = lookupClient.lookup(rawNip);
    jdbc.update(
        """
        UPDATE investory.ryczalt_onboarding
           SET nip = ?, company_name = ?, regon = ?, legal_form = ?, business_status = ?,
               business_start_date = ?, vat_status = ?, registered_address = ?, company_source = ?,
               lookup_status = ?, lookup_warnings = ?, company_lookup_retrieved_at = ?, updated_at = CURRENT_TIMESTAMP
         WHERE profile_id = ?
        """,
        result.nip(),
        result.companyName(),
        result.regon(),
        result.legalForm(),
        result.businessStatus(),
        result.businessStartDate(),
        result.vatStatus(),
        result.registeredAddress(),
        result.source(),
        result.lookupStatus(),
        String.join(",", result.warnings()),
        Timestamp.from(result.retrievedAt()),
        profileId);
    return result;
  }

  @Transactional
  public RyczaltOnboarding confirmCompany(long profileId, CompanyConfirmation request) {
    requireProfile(profileId);
    if (request == null) throw badRequest("company_confirmation_invalid");
    String nip = NipValidator.normalize(request.nip());
    if (!NipValidator.isValid(nip)
        || request.companyName() == null
        || request.companyName().isBlank()
        || (request.legalForm() != null && !"JDG".equals(request.legalForm())))
      throw badRequest("company_confirmation_invalid");
    String source =
        request.source() == null || request.source().isBlank()
            ? "USER_CONFIRMED"
            : request.source();
    jdbc.update(
        """
        UPDATE investory.ryczalt_onboarding
           SET state = 'COMPANY_CONFIRMED', nip = ?, company_name = ?, regon = ?, legal_form = ?,
               business_status = ?, business_start_date = ?, vat_status = ?, registered_address = ?,
               company_source = ?, lookup_status = ?, lookup_warnings = NULL, company_confirmed_at = CURRENT_TIMESTAMP,
               updated_at = CURRENT_TIMESTAMP
         WHERE profile_id = ?
        """,
        nip,
        request.companyName().trim(),
        emptyToNull(request.regon()),
        "JDG",
        "ACTIVE",
        request.businessStartDate(),
        emptyToNull(request.vatStatus()),
        emptyToNull(request.registeredAddress()),
        source,
        "COMPLETE",
        null,
        profileId);
    return read(profileId);
  }

  @Transactional
  public RyczaltOnboarding confirmAccounting(long profileId, AccountingConfirmation request) {
    requireProfile(profileId);
    if (request == null) throw badRequest("accounting_configuration_not_supported");
    if (!"COMPANY_CONFIRMED".equals(read(profileId).state()))
      throw badRequest("company_confirmation_required");
    if (!"JDG".equals(request.legalForm())
        || !"RYCZALT".equals(request.taxation())
        || request.ryczaltRate() != 12
        || !"MONTHLY".equals(request.pitFrequency())
        || !request.vatRegistered()
        || !"MONTHLY".equals(request.vatFrequency())
        || request.accountingStartDate() == null
        || request.accountingStartDate().isAfter(LocalDate.now()))
      throw badRequest("accounting_configuration_not_supported");
    jdbc.update(
        """
        UPDATE investory.ryczalt_onboarding
           SET state = 'ACCOUNTING_CONFIRMED', legal_form = 'JDG', taxation_method = 'RYCZALT',
               ryczalt_rate = 0.12, pit_frequency = 'MONTHLY', vat_status = 'ACTIVE', vat_frequency = 'MONTHLY',
               accounting_start_date = ?, accounting_confirmed_at = CURRENT_TIMESTAMP,
               updated_at = CURRENT_TIMESTAMP
         WHERE profile_id = ?
        """,
        request.accountingStartDate(),
        profileId);
    return read(profileId);
  }

  @Transactional
  public RyczaltOnboarding saveZus(long profileId, ZusConfirmation request) {
    requireProfile(profileId);
    if (request == null) throw badRequest("zus_configuration_invalid");
    if (!request.jdgActive()
        || request.qualifyingUop() == null
        || request.voluntarySickness() == null
        || request.healthMethod() == null
        || request.healthMethod().isBlank()) throw badRequest("zus_configuration_invalid");
    jdbc.update(
        """
        UPDATE investory.ryczalt_onboarding
           SET jdg_active = ?, qualifying_uop = ?, zus_regime = 'JDG', voluntary_sickness = ?,
               zus_health_method = ?, zus_full_jdg_social = ?, updated_at = CURRENT_TIMESTAMP
         WHERE profile_id = ?
        """,
        request.jdgActive(),
        request.qualifyingUop(),
        request.voluntarySickness(),
        request.healthMethod(),
        request.fullJdgSocial(),
        profileId);
    return read(profileId);
  }

  @Transactional
  public RyczaltOnboarding complete(long profileId, String ksefState) {
    requireProfile(profileId);
    if (!"SKIPPED".equals(ksefState) && !"CONNECTED".equals(ksefState))
      throw badRequest("ksef_choice_required");
    RyczaltOnboarding current = read(profileId);
    if ("COMPLETED".equals(current.state())) return current;
    if (!"ACCOUNTING_CONFIRMED".equals(current.state())
        || current.jdgActive() == null
        || current.qualifyingUop() == null
        || current.voluntarySickness() == null) throw badRequest("onboarding_steps_incomplete");
    jdbc.update(
        """
        UPDATE investory.ryczalt_onboarding
           SET state = 'COMPLETED', ksef_state = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
         WHERE profile_id = ?
        """,
        ksefState,
        profileId);
    jdbc.update(
        """
        INSERT INTO investory.ryczalt_profile (profile_id, nip, full_name, has_uop)
        SELECT profile_id, nip, company_name, qualifying_uop
          FROM investory.ryczalt_onboarding WHERE profile_id = ?
        ON CONFLICT (profile_id) DO NOTHING
        """,
        profileId);
    jdbc.update(
        """
        UPDATE investory.ryczalt_profile p
           SET nip = o.nip, full_name = o.company_name, has_uop = o.qualifying_uop
          FROM investory.ryczalt_onboarding o
         WHERE p.profile_id = o.profile_id AND o.profile_id = ?
        """,
        profileId);
    return read(profileId);
  }

  @Transactional
  public RyczaltOnboarding skipKsef(long profileId) {
    requireProfile(profileId);
    jdbc.update(
        "UPDATE investory.ryczalt_onboarding SET ksef_state = 'SKIPPED', updated_at = CURRENT_TIMESTAMP WHERE profile_id = ?",
        profileId);
    return read(profileId);
  }

  @Transactional
  public RyczaltOnboarding markKsefConnected(long profileId) {
    requireProfile(profileId);
    jdbc.update(
        "UPDATE investory.ryczalt_onboarding SET ksef_state = 'CONNECTED', updated_at = CURRENT_TIMESTAMP WHERE profile_id = ?",
        profileId);
    return read(profileId);
  }

  private RyczaltOnboarding read(long profileId) {
    return jdbc.queryForObject(
        """
        SELECT profile_id, state, nip, company_name, regon, legal_form, business_status, business_start_date, vat_status, registered_address,
               company_source, lookup_status, lookup_warnings, company_lookup_retrieved_at, company_confirmed_at,
               zus_regime, jdg_active, qualifying_uop, voluntary_sickness, zus_health_method, zus_full_jdg_social,
               taxation_method, ryczalt_rate, pit_frequency, vat_frequency, accounting_start_date, ksef_state, completed_at
          FROM investory.ryczalt_onboarding WHERE profile_id = ?
        """,
        (rs, row) ->
            new RyczaltOnboarding(
                rs.getLong("profile_id"),
                rs.getString("state"),
                rs.getString("nip"),
                rs.getString("company_name"),
                rs.getString("regon"),
                rs.getString("legal_form"),
                rs.getString("business_status"),
                rs.getObject("business_start_date", java.time.LocalDate.class),
                rs.getString("vat_status"),
                rs.getString("registered_address"),
                rs.getString("company_source"),
                rs.getString("lookup_status"),
                rs.getString("lookup_warnings"),
                instant(rs.getTimestamp("company_lookup_retrieved_at")),
                instant(rs.getTimestamp("company_confirmed_at")),
                rs.getString("zus_regime"),
                (Boolean) rs.getObject("jdg_active"),
                (Boolean) rs.getObject("qualifying_uop"),
                (Boolean) rs.getObject("voluntary_sickness"),
                rs.getString("zus_health_method"),
                decimal(rs.getBigDecimal("zus_full_jdg_social")),
                rs.getString("taxation_method"),
                decimal(rs.getBigDecimal("ryczalt_rate")),
                rs.getString("pit_frequency"),
                rs.getString("vat_frequency"),
                rs.getObject("accounting_start_date", java.time.LocalDate.class),
                rs.getString("ksef_state"),
                instant(rs.getTimestamp("completed_at"))),
        profileId);
  }

  private void ensureRow(long profileId) {
    requireProfile(profileId);
    jdbc.update(
        """
        INSERT INTO investory.ryczalt_onboarding (profile_id) VALUES (?) ON CONFLICT (profile_id) DO NOTHING
        """,
        profileId);
  }

  private void requireProfile(long profileId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM investory.portfolios WHERE id = ?", Integer.class, profileId);
    if (count == null || count == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "profile_not_found");
    ensureExistingRowOnly(profileId);
  }

  private void ensureExistingRowOnly(long profileId) {
    jdbc.update(
        "INSERT INTO investory.ryczalt_onboarding (profile_id) VALUES (?) ON CONFLICT (profile_id) DO NOTHING",
        profileId);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String decimal(java.math.BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public record CompanyConfirmation(
      String nip,
      String companyName,
      String regon,
      String vatStatus,
      String registeredAddress,
      String source,
      String legalForm,
      LocalDate businessStartDate) {}

  public record AccountingConfirmation(
      String legalForm,
      String taxation,
      int ryczaltRate,
      String pitFrequency,
      boolean vatRegistered,
      String vatFrequency,
      LocalDate accountingStartDate) {}

  public record ZusConfirmation(
      boolean jdgActive,
      Boolean qualifyingUop,
      Boolean voluntarySickness,
      String healthMethod,
      java.math.BigDecimal fullJdgSocial) {}
}
