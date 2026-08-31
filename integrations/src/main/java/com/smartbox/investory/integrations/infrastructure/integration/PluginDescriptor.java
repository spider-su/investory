package com.smartbox.investory.integrations.infrastructure.integration;

import java.util.List;

public record PluginDescriptor(
    String id,
    String name,
    IntegrationType type,
    List<PluginFieldDescriptor> configuration,
    List<String> jobs) {
  public PluginDescriptor {
    configuration = configuration == null ? List.of() : List.copyOf(configuration);
    jobs = jobs == null ? List.of() : List.copyOf(jobs);
  }

  public String description() {
    return name + " integration";
  }

  public List<IntegrationJobDescriptor> jobDescriptors() {
    return jobs.stream().map(IntegrationJobDescriptor::forType).toList();
  }
}
