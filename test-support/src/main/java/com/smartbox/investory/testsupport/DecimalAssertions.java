package com.smartbox.investory.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/** Rounded amount assertions only; exact identities and rate contracts keep exact comparisons. */
public final class DecimalAssertions {
  private DecimalAssertions() {}

  /** Both amounts must round to the same value at scale 3, using HALF_UP. */
  public static void assertEqualToThreeDecimals(BigDecimal actual, BigDecimal expected) {
    assertThat(actual).as("actual amount").isNotNull();
    assertThat(expected).as("expected amount").isNotNull();
    assertThat(rounded(actual))
        .as("amount rounded to 3 decimal places (HALF_UP)")
        .isEqualByComparingTo(rounded(expected));
  }

  /** For JSON numeric amounts; converts the numeric representation without double arithmetic. */
  public static Matcher<Number> equalToThreeDecimals(BigDecimal expected) {
    assertThat(expected).as("expected amount").isNotNull();
    return new TypeSafeDiagnosingMatcher<Number>() {
      @Override
      protected boolean matchesSafely(Number actual, Description mismatch) {
        BigDecimal amount;
        try {
          amount = new BigDecimal(actual.toString());
        } catch (NumberFormatException invalid) {
          mismatch.appendText("non-finite number ").appendValue(actual);
          return false;
        }
        if (rounded(amount).compareTo(rounded(expected)) == 0) return true;
        mismatch
            .appendText("was ")
            .appendValue(actual)
            .appendText(" (rounded: ")
            .appendValue(rounded(amount))
            .appendText(")");
        return false;
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("amount rounding to ")
            .appendValue(rounded(expected))
            .appendText(" at 3 decimal places (HALF_UP)");
      }
    };
  }

  private static BigDecimal rounded(BigDecimal value) {
    return value.setScale(3, RoundingMode.HALF_UP);
  }
}
