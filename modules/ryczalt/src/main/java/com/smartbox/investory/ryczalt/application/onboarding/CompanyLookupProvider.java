package com.smartbox.investory.ryczalt.application.onboarding;

/** Official-register boundary. Providers must never fabricate missing fields. */
public interface CompanyLookupProvider {
  CompanyLookupResult lookup(String nip);
}
