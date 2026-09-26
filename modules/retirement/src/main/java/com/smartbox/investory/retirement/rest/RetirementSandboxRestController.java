package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementSandboxApi;
import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process adapter for the retirement sandbox. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/sandbox")
public class RetirementSandboxRestController {
  private final RetirementSandboxApi sandbox;

  public RetirementSandboxRestController(
      @Qualifier("retirementSandboxSimulationService") RetirementSandboxApi sandbox) {
    this.sandbox = sandbox;
  }

  @PostMapping
  public SimulationResult simulate(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody SandboxSimulationInput input) {
    return sandbox.simulate(input);
  }
}
