package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import org.springframework.web.server.ResponseStatusException;

/** Small shared helpers for the native Ryczalt MVC controllers. */
final class RyczaltAccountingWebSupport {
  private static final System.Logger LOG =
      System.getLogger(RyczaltAccountingWebSupport.class.getName());

  private RyczaltAccountingWebSupport() {}

  static boolean canWrite(HttpServletRequest request) {
    return request.isUserInRole("ADMIN") || request.isUserInRole("PROFILE_OWNER");
  }

  static void logFailure(String operation, long profileId, YearMonth month, Exception exception) {
    if (exception instanceof ResponseStatusException status
        && status.getStatusCode().is4xxClientError()) {
      LOG.log(
          System.Logger.Level.WARNING,
          "Web accounting operation rejected: operation="
              + operation
              + ", profileId="
              + profileId
              + ", month="
              + month
              + ", status="
              + status.getStatusCode().value());
    } else {
      LOG.log(
          System.Logger.Level.ERROR,
          "Web accounting operation failed: operation="
              + operation
              + ", profileId="
              + profileId
              + ", month="
              + month,
          exception);
    }
  }

  static String userMessage(Exception exception, String fallback) {
    if (!(exception instanceof ResponseStatusException status)) return fallback;
    int code = status.getStatusCode().value();
    if (code == 403) return "You are not allowed to perform this action.";
    if (code == 404) return "The requested accounting record was not found.";
    if (code == 409) return "The accounting period changed. Reload the page and try again.";
    if (code == 400 && status.getReason() != null && !status.getReason().isBlank())
      return status.getReason();
    return fallback;
  }

  static String accountingRedirect(long profileId, YearMonth month) {
    return "redirect:/profiles/"
        + profileId
        + "/accounting"
        + (month == null ? "" : "?month=" + month);
  }

  static String counterpartyRedirect(long profileId, long counterpartyId) {
    return "redirect:/profiles/" + profileId + "/accounting/counterparties/" + counterpartyId;
  }
}
