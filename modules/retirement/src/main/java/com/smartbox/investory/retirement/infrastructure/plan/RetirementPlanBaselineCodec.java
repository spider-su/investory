package com.smartbox.investory.retirement.infrastructure.plan;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Encodes the versioned Long-Term planning snapshot owned by a retirement plan. */
@Component
public final class RetirementPlanBaselineCodec {
  public static final int CURRENT_FORMAT_VERSION = 1;
  private final ObjectMapper json;

  public RetirementPlanBaselineCodec(ObjectMapper json) {
    this.json = json;
  }

  public String write(ProfileAssetProjection state) {
    try {
      return json.writeValueAsString(
          java.util.Map.of("version", CURRENT_FORMAT_VERSION, "payload", state));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to persist Long-Term planning baseline", e);
    }
  }

  public ProfileAssetProjection read(String value) {
    if (value == null || isBlank(value)) return ProfileAssetProjection.EMPTY;
    try {
      var tree = json.readTree(value);
      if (tree.has("payload")) {
        var version = tree.get("version");
        if (version == null || !version.canConvertToInt())
          throw new IllegalStateException("Long-Term planning baseline version is missing");
        if (version.intValue() != CURRENT_FORMAT_VERSION)
          throw new IllegalStateException(
              "Unsupported Long-Term planning baseline version: " + version.intValue());
        return json.treeToValue(tree.get("payload"), ProfileAssetProjection.class);
      }
      return json.treeToValue(tree, ProfileAssetProjection.class);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read Long-Term planning baseline", e);
    }
  }
}
