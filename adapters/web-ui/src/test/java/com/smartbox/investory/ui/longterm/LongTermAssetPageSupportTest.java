package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

class LongTermAssetPageSupportTest {
  @Test
  void buildsPortfolioScopedRentalRedirect() {
    assertThat(LongTermAssetPageSupport.rentalRedirect(42L, 7L))
        .isEqualTo("redirect:/portfolios/7/long-term-assets/42/real-estate#rental-contracts");
  }

  @Test
  void appliesMutationAndDoesNotCreateFeedbackWhenActionSucceeds() {
    Runnable action = mock();
    RedirectAttributes feedback = mock();

    LongTermAssetPageSupport.applyAssetMutation(action, feedback);

    verify(action).run();
    verifyNoInteractions(feedback);
  }

  @Test
  void mapsAssetTypeFailureToStableUserMessage() {
    RedirectAttributes feedback = mock();

    LongTermAssetPageSupport.applyAssetMutation(
        () -> {
          throw new IllegalArgumentException("asset type is immutable");
        },
        feedback);

    verify(feedback).addFlashAttribute("error", "Asset type cannot be changed.");
  }

  @Test
  void mapsRentalValidationFailuresToSpecificMessages() {
    assertThat(LongTermAssetPageSupport.rentalError(new IllegalArgumentException("bad email")))
        .isEqualTo("Enter a valid tenant email address.");
    assertThat(LongTermAssetPageSupport.rentalError(new IllegalArgumentException("amount invalid")))
        .isEqualTo("Enter valid, non-negative contract amounts.");
    assertThat(
            LongTermAssetPageSupport.rentalError(
                new IllegalArgumentException("overlapping rental")))
        .isEqualTo("This contract overlaps another rental contract.");
    assertThat(LongTermAssetPageSupport.rentalError(new IllegalArgumentException("not found")))
        .isEqualTo("Rental contract or property was not found.");
    assertThat(LongTermAssetPageSupport.rentalError(new IllegalArgumentException("unexpected")))
        .isEqualTo("Rental contract could not be saved. Check the entered values.");
  }

  @Test
  void preservesRejectedFieldsAndBindingMessages() {
    BindingResult binding = mock();
    RedirectAttributes feedback = mock();
    FieldError error =
        new FieldError(
            "contract", "tenantEmail", "bad@example", false, null, null, "invalid email");
    when(binding.getModel()).thenReturn(Map.of("contract", "bound"));
    when(binding.getFieldErrors()).thenReturn(List.of(error));

    LongTermAssetPageSupport.preserveBindingErrors(binding, feedback);

    verify(feedback).addFlashAttribute("contract", "bound");
    verify(feedback).addFlashAttribute("error", "Check the highlighted contract fields.");
    verify(feedback)
        .addFlashAttribute("rentalRejectedValues", Map.of("tenantEmail", "bad@example"));
    verify(feedback)
        .addFlashAttribute("rentalBindingErrors", List.of("Invalid tenant email: bad@example."));
  }
}
