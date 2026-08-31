package com.smartbox.investory.retirement.infrastructure.simulation;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.retirement.api.model.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Encodes the versioned public planning snapshot stored inside a plan revision. */
@Component
public final class PlanningBaselineJsonCodec {
  private final ObjectMapper json;

  public PlanningBaselineJsonCodec(ObjectMapper json) {
    this.json = json;
  }

  public String write(ProfileAssetProjection state) {
    try {
      return json.writeValueAsString(java.util.Map.of("version", 1, "payload", state));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to persist Long-Term planning baseline", e);
    }
  }

  public ProfileAssetProjection read(String value) {
    if (value == null || isBlank(value)) return ProfileAssetProjection.EMPTY;
    try {
      var tree = json.readTree(value);
      if (tree.has("payload"))
        return json.treeToValue(tree.get("payload"), ProfileAssetProjection.class);
      // Legacy V01.020 payloads were unwrapped; keep them readable during migration.
      return json.treeToValue(tree, ProfileAssetProjection.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read Long-Term planning baseline", e);
    }
  }
}
