package com.smartbox.investory.rag;

import com.smartbox.investory.profile.api.contract.ProfileResponse;
import java.util.List;

/** Outbound adapter contract: Java is responsible for authorization and financial truth. */
public interface InvestoryRagClient {
  RagAnswer ask(String question, ProfileResponse snapshot);

  /** Documentation-only question; no portfolio snapshot is forwarded. */
  RagAnswer askDocumentation(String question);

  /** Public ticker research only; portfolio or user details must never cross this boundary. */
  MarketData marketData(String tool, String symbol);

  record MarketData(String provider, String tool, String symbol, String source, String data) {}

  record RagAnswer(String answer, List<String> sources) {}
}
