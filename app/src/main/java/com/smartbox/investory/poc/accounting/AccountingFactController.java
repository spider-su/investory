package com.smartbox.investory.poc.accounting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AccountingFactController {
  private final AccountingFactService service;

  @GetMapping("/poc/accounting")
  public String facts(Model model) {
    model.addAttribute("facts", service.facts());
    return "poc/accounting-facts";
  }
}
