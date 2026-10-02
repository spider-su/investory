package com.smartbox.investory.retirement.infrastructure.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RetirementPlanBaselineCodecTest {
  private final RetirementPlanBaselineCodec codec =
      new RetirementPlanBaselineCodec(new ObjectMapper());

  @Test
  void readsCurrentVersionedBaseline() {
    String encoded = codec.write(ProfileAssetProjection.EMPTY);

    assertThat(codec.read(encoded)).isEqualTo(ProfileAssetProjection.EMPTY);
  }

  @Test
  void rejectsUnknownVersionWithClearError() {
    assertThatThrownBy(() -> codec.read("{\"version\":2,\"payload\":{\"assets\":[]}}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported Long-Term planning baseline version: 2");
  }

  @Test
  void acceptsLegacyRawBaselineWithoutWrapperVersion() throws Exception {
    String legacy = new ObjectMapper().writeValueAsString(ProfileAssetProjection.EMPTY);

    assertThat(codec.read(legacy)).isEqualTo(ProfileAssetProjection.EMPTY);
  }
}
