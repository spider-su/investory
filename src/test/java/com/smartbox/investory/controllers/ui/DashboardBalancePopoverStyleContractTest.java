package com.smartbox.investory.controllers.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardBalancePopoverStyleContractTest {

  @Test
  void balancePopoverOwnsAVisibleStackingContextAboveFloatingNavigation() throws Exception {
    String css =
        Files.readString(Path.of("src/main/resources/static/css/main.css"), StandardCharsets.UTF_8);

    assertThat(css)
        .contains(".iv-topbar:has(#balance-cash[open]) { z-index: 1210; }")
        .contains(".iv-balance-cash-popover { position: relative; z-index: 20; }")
        .contains(".iv-balance-cash-popover > .iv-balance-popover__panel { z-index: 30; }")
        .contains(".iv-topbar-summary,\n.iv-balance-cash-popover")
        .contains(".iv-balance-cash-popover { overflow: visible; }")
        .contains(".iv-header-rail__accounts {\n    position: relative;")
        .contains(".iv-header-rail__accounts > .iv-balance-popover__panel")
        .contains(".iv-header-rail__right .iv-data-quality-popover {\n    position: relative;")
        .contains("width: min(440px, calc(100vw - 32px));");
  }
}
