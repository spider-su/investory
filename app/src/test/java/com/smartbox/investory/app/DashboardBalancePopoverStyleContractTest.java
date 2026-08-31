package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dashboard Balance Popover Style Contract")
class DashboardBalancePopoverStyleContractTest {

  @DisplayName("balance Popover Owns AVisible Stacking Context Above Floating Navigation")
  @Test
  void balancePopoverOwnsAVisibleStackingContextAboveFloatingNavigation() throws Exception {
    String css =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/css/components.css"),
            StandardCharsets.UTF_8);

    assertThat(css)
        .contains(".iv-topbar:has(#balance-cash[open]) { z-index: 1210; }")
        .contains(".iv-balance-cash-popover { position: relative; z-index: 20; }")
        .contains(".iv-balance-cash-popover > .iv-balance-popover__panel { z-index: 30; }")
        .contains(".iv-topbar-summary,")
        .contains(".iv-balance-cash-popover")
        .contains(".iv-balance-cash-popover { overflow: visible; }")
        .contains(".iv-header-rail__accounts {")
        .contains("position: relative;")
        .contains(".iv-header-rail__accounts > .iv-balance-popover__panel")
        .contains(".iv-header-rail__right .iv-data-quality-popover {")
        .contains("position: relative;")
        .contains("width: min(440px, calc(100vw - 32px));");
  }
}
