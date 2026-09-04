package com.smartbox.investory.profile.api.model;

import static com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull;

import java.math.BigDecimal;

/** Source-total checks captured while composing profile allocation. */
public record ProfileAllocationReconciliation(SourceTotal shortTerm, SourceTotal longTerm) {
  public static final ProfileAllocationReconciliation EMPTY =
      new ProfileAllocationReconciliation(SourceTotal.EMPTY, SourceTotal.EMPTY);

  public ProfileAllocationReconciliation {
    shortTerm = shortTerm == null ? SourceTotal.EMPTY : shortTerm;
    longTerm = longTerm == null ? SourceTotal.EMPTY : longTerm;
  }

  public boolean balanced() {
    return shortTerm.balanced() && longTerm.balanced();
  }

  /**
   * Whether allocation percentages should be treated as approximate.
   *
   * <p>Unbalanced source totals do not alter classified allocation values. In that case,
   * percentages are calculated from the classified total and this flag exposes the limitation to
   * consumers.
   */
  public boolean percentagesApproximate() {
    return !balanced();
  }

  public record SourceTotal(BigDecimal classifiedValue, BigDecimal authoritativeValue) {
    public static final SourceTotal EMPTY = new SourceTotal(BigDecimal.ZERO, BigDecimal.ZERO);

    public SourceTotal {
      classifiedValue = zeroIfNull(classifiedValue);
      authoritativeValue = zeroIfNull(authoritativeValue);
    }

    public BigDecimal delta() {
      return authoritativeValue.subtract(classifiedValue);
    }

    public boolean balanced() {
      return delta().signum() == 0;
    }
  }
}
