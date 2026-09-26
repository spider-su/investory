package com.smartbox.investory.ryczalt.application.onboarding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Combines provider facts without allowing an empty response to erase a known value. */
@Component
public class CompanyLookupAggregator implements CompanyLookupClient {
  private final List<CompanyLookupProvider> providers;

  public CompanyLookupAggregator(List<CompanyLookupProvider> providers) {
    this.providers = providers;
  }

  @Override
  public CompanyLookupResult lookup(String nip) {
    String companyName = null, regon = null, vatStatus = null, address = null;
    String legalForm = null, businessStatus = null;
    java.time.LocalDate startDate = null;
    List<String> sources = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    boolean found = false;
    boolean unavailable = false;
    for (CompanyLookupProvider provider : providers) {
      try {
        CompanyLookupResult result = provider.lookup(nip);
        if (result == null) continue;
        if ("UNAVAILABLE".equals(result.lookupStatus())) unavailable = true;
        if (result.lookupStatus() != null
            && !"NOT_FOUND".equals(result.lookupStatus())
            && !"UNAVAILABLE".equals(result.lookupStatus())) found = true;
        if (result.companyName() != null && companyName == null) companyName = result.companyName();
        else if (result.companyName() != null && !result.companyName().equals(companyName))
          warnings.add("conflicting_company_name");
        if (result.regon() != null && regon == null) regon = result.regon();
        else if (result.regon() != null && !result.regon().equals(regon))
          warnings.add("conflicting_regon");
        if (result.vatStatus() != null && vatStatus == null) vatStatus = result.vatStatus();
        else if (result.vatStatus() != null && !result.vatStatus().equals(vatStatus))
          warnings.add("conflicting_vat_status");
        if (result.registeredAddress() != null && address == null)
          address = result.registeredAddress();
        if (result.legalForm() != null && legalForm == null) legalForm = result.legalForm();
        if (result.businessStatus() != null && businessStatus == null)
          businessStatus = result.businessStatus();
        if (result.businessStartDate() != null && startDate == null)
          startDate = result.businessStartDate();
        if (result.sources() != null) sources.addAll(result.sources());
        if (result.warnings() != null) warnings.addAll(result.warnings());
      } catch (RuntimeException exception) {
        warnings.add(
            exception.getMessage() == null ? "provider_unavailable" : exception.getMessage());
      }
    }
    if (!found && sources.isEmpty()) {
      throw new IllegalStateException("company_lookup_unavailable");
    }
    String status =
        !found
            ? unavailable ? "UNAVAILABLE" : "NOT_FOUND"
            : warnings.isEmpty() && companyName != null && vatStatus != null
                ? "COMPLETE"
                : "PARTIAL";
    return new CompanyLookupResult(
        nip,
        companyName,
        regon,
        vatStatus,
        address,
        String.join(",", sources),
        Instant.now(),
        legalForm,
        businessStatus,
        startDate,
        List.copyOf(sources),
        List.copyOf(warnings),
        status);
  }
}
