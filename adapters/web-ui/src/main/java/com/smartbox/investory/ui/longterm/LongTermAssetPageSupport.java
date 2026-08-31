package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

final class LongTermAssetPageSupport {
  private LongTermAssetPageSupport() {}

  static String share(BigDecimal value, BigDecimal total) {
    return total == null || total.signum() == 0
        ? "0.0%"
        : FinancialPresentation.percentage(value.divide(total, 8, java.math.RoundingMode.HALF_UP));
  }

  static String assetRedirect(Long id, Long portfolioId) {
    return "redirect:/long-term-assets/" + id + "?portfolioId=" + portfolioId;
  }

  static String rentalRedirect(Long id, Long portfolioId) {
    return assetRedirect(id, portfolioId) + "#rental-contracts";
  }

  static String taxPolicyRedirect(Long portfolioId) {
    return "redirect:/long-term-assets?portfolioId=" + portfolioId + "#rental-tax-policies";
  }

  static void applyAssetMutation(Runnable action, RedirectAttributes feedback) {
    try {
      action.run();
    } catch (IllegalArgumentException | ResourceNotFoundException exception) {
      feedback.addFlashAttribute("error", assetError(exception));
    }
  }

  static String assetError(RuntimeException exception) {
    String message = exception.getMessage();
    if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("type"))
      return "Asset type cannot be changed.";
    return message == null || message.isBlank() ? "Long-term asset could not be updated." : message;
  }

  static String rentalError(RuntimeException exception) {
    String message =
        exception.getMessage() == null
            ? ""
            : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
    if (message.contains("email")) return "Enter a valid tenant email address.";
    if (message.contains("amount") || message.contains("term"))
      return "Enter valid, non-negative contract amounts.";
    if (message.contains("overlapping")) return "This contract overlaps another rental contract.";
    if (message.contains("start") || message.contains("end") || message.contains("termination"))
      return "Check the contract start, expected end, and termination dates.";
    if (message.contains("real-estate")) return "Rental contracts require a real-estate asset.";
    if (message.contains("not found")) return "Rental contract or property was not found.";
    return "Rental contract could not be saved. Check the entered values.";
  }

  static void preserveBindingErrors(BindingResult binding, RedirectAttributes feedback) {
    binding.getModel().forEach(feedback::addFlashAttribute);
    feedback.addFlashAttribute("error", "Check the highlighted contract fields.");
    feedback.addFlashAttribute(
        "rentalRejectedValues",
        binding.getFieldErrors().stream()
            .filter(error -> error.getRejectedValue() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    org.springframework.validation.FieldError::getField,
                    error -> String.valueOf(error.getRejectedValue()),
                    (first, ignored) -> first)));
    feedback.addFlashAttribute(
        "rentalBindingErrors",
        binding.getFieldErrors().stream()
            .map(
                error ->
                    "Invalid "
                        + error.getField().replaceAll("([A-Z])", " $1").toLowerCase()
                        + (error.getRejectedValue() == null
                            ? "."
                            : ": " + error.getRejectedValue() + "."))
            .distinct()
            .toList());
  }
}
