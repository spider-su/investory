package com.smartbox.investory.controllers.ui;

import com.smartbox.investory.config.BuildMetadata;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Shared presentation model values used by the main Thymeleaf pages. */
@ControllerAdvice
public class GlobalUiModelAttributes {
  private final BuildMetadata buildMetadata;

  public GlobalUiModelAttributes(ObjectProvider<BuildMetadata> buildMetadata) {
    this.buildMetadata = buildMetadata.getIfAvailable(BuildMetadata::development);
  }

  @ModelAttribute("buildMetadata")
  public BuildMetadata buildMetadata() {
    return buildMetadata;
  }

  @ModelAttribute("format")
  public UiPresentation format() {
    return new UiPresentation();
  }
}
