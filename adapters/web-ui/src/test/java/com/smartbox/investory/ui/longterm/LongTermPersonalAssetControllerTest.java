package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.PersonalAssetCategory;
import com.smartbox.investory.longterm.api.model.PersonalAssetView;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class LongTermPersonalAssetControllerTest {
  @Test
  void editLoadsPersistedCategoryAndUnrelatedFields() {
    LongTermAssetsClient assets = mock(LongTermAssetsClient.class);
    var controller =
        new LongTermPersonalAssetController(
            assets, Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
    var persisted =
        new PersonalAssetView(
            12L,
            7L,
            "Family home",
            PersonalAssetCategory.HOME,
            CurrencyType.PLN,
            LocalDate.of(2018, 5, 1),
            new BigDecimal("650000"),
            true,
            "Do not sell before 2030");
    when(assets.personalAsset(7L, 12L)).thenReturn(persisted);
    var model = new ConcurrentModel();

    assertThat(controller.edit(7L, 12L, model)).isEqualTo("personal-asset-form");
    var form = (PersonalAssetForm) model.getAttribute("asset");
    assertThat(form.getCategory()).isEqualTo(PersonalAssetCategory.HOME);
    assertThat(form.getAcquisitionDate()).isEqualTo(persisted.acquisitionDate());
    assertThat(form.getNotes()).isEqualTo(persisted.notes());
    verify(assets).personalAsset(7L, 12L);
  }
}
