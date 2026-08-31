package com.smartbox.investory.retirement.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.retirement.api.RetirementPreviewApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.EditorPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class RetirementPreviewRestControllerIT {
  private RetirementPreviewApi previews;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    previews = mock(RetirementPreviewApi.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new RetirementPreviewRestController(previews))
            .setValidator(validator)
            .build();
  }

  @Test
  void rejectsIncompleteEditorRequest() throws Exception {
    mvc.perform(
            post("/api/v1/retirement/portfolios/7/preview")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(previews);
  }

  @Test
  void bindsTypedEditorRequestAndPortfolioPath() throws Exception {
    when(previews.editorPreview(
            eq(7L), isNull(), eq(com.smartbox.investory.shared.currency.CurrencyType.PLN), any()))
        .thenReturn(new EditorPreviewResponse(true, java.util.List.of(), null, null));

    mvc.perform(
            post("/api/v1/retirement/portfolios/7/preview")
                .contentType("application/json")
                .content(
                    "{\"ageAtPlanStart\":40,\"startYear\":2026,"
                        + "\"endAge\":95,\"inflation\":2.5,"
                        + "\"equityReturn\":6}"))
        .andExpect(status().isOk());

    verify(previews)
        .editorPreview(
            eq(7L), isNull(), eq(com.smartbox.investory.shared.currency.CurrencyType.PLN), any());
  }
}
