package com.smartbox.investory.accounting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Crosses security, REST routing, the user facade and the isolated accounting database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Accounting user REST boundary")
class AccountingRestControllerIT extends AccountingDatabaseTest {
  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("admin can read the monthly overview and detail resources")
  void adminReadsOverviewAndDetails() throws Exception {
    var admin = user("admin").roles("ADMIN");

    mvc.perform(get("/api/profiles/1/accounting/months").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].month").exists())
        .andExpect(jsonPath("$[0].lifecycleLabel").exists());

    mvc.perform(get("/api/profiles/1/accounting/months/2026-01/overview").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.month").value("2026-01"))
        .andExpect(jsonPath("$.summary").exists())
        .andExpect(jsonPath("$.sources.imported").exists());

    mvc.perform(get("/api/profiles/1/accounting/months/2026-01/issues").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    mvc.perform(get("/api/profiles/1/accounting/months/2026-01/documents").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    mvc.perform(get("/api/profiles/1/accounting/months/2026-01/bank-transactions").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    mvc.perform(get("/api/profiles/1/accounting/months/2026-01/reconciliation").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @DisplayName("a non-owner cannot call accounting lifecycle actions")
  void nonOwnerCannotConfirm() throws Exception {
    mvc.perform(
            post("/api/profiles/1/accounting/months/2026-01/confirm")
                .with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("a profile user cannot acquire accounting sources")
  void profileUserCannotAcquireSources() throws Exception {
    var user = user("user").roles("USER");
    var document = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[] {1});
    var bank = new MockMultipartFile("file", "bank.csv", "text/csv", new byte[] {1});

    mvc.perform(
            multipart("/api/profiles/1/accounting/documents/recognize").file(document).with(user))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/profiles/1/accounting/bank/import")
                .file(bank)
                .param("month", "2026-01")
                .with(user))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/profiles/1/accounting/ksef/sync").param("month", "2026-01").with(user))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("unauthenticated accounting reads are rejected")
  void unauthenticatedReadIsRejected() throws Exception {
    mvc.perform(get("/api/profiles/1/accounting/months")).andExpect(status().isUnauthorized());
  }
}
