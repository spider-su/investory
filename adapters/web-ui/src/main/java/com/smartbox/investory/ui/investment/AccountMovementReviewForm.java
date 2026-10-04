package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class AccountMovementReviewForm {
  @NotBlank private String issueCode;
  @NotNull private Long accountId;
  @NotNull private LocalDate eventDate;

  @NotBlank
  @Pattern(regexp = "[0-9a-f]{32}")
  private String eventFingerprint;

  @NotNull private AccountMovementReviewResolution resolution;
  @NotBlank private String rationale;
  @NotBlank private String evidenceReference;
}
