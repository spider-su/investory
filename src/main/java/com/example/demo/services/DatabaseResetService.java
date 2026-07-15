package com.example.demo.services;

import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.imports.ImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DatabaseResetService {

  private final CashOperationRepository cashOperationRepository;
  private final ClosedPositionRepository closedPositionRepository;
  private final CurrencyRateRepository currencyRateRepository;
  private final ImportRepository importRepository;
  private final OpenedPositionRepository openedPositionRepository;
  private final AccountDailyRepository accountDailyRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;

  public String resetImportedData() {
    long cash = cashOperationRepository.count();
    long closed = closedPositionRepository.count();
    long open = openedPositionRepository.count();
    long imports = importRepository.count();
    long fx = currencyRateRepository.count();

    accountDailyRepository.deleteAllInBatch();
    accountStatisticsRepository.deleteAllInBatch();
    cashOperationRepository.deleteAllInBatch();
    closedPositionRepository.deleteAllInBatch();
    openedPositionRepository.deleteAllInBatch();
    importRepository.deleteAllInBatch();
    currencyRateRepository.deleteAllInBatch();

    return String.format(
        "Reset imported data: %d cash, %d closed, %d open, %d imports, %d FX rows removed",
        cash, closed, open, imports, fx);
  }
}

