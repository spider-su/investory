package com.smartbox.investory.poc.accounting;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFactService {
  private final AccountingFactRepository repository;

  public List<AccountingFact> facts() {
    return repository.findAll();
  }
}
