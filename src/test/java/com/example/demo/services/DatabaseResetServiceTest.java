package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.repository.CashOperationRepository;
import com.example.demo.infrastructure.repository.ClosedPositionRepository;
import com.example.demo.infrastructure.repository.CurrencyRateRepository;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.infrastructure.repository.account.AccountDailyRepository;
import com.example.demo.infrastructure.repository.account.AccountStatisticsRepository;
import com.example.demo.infrastructure.repository.imports.ImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseResetServiceTest {

  @Mock private CashOperationRepository cashOperationRepository;
  @Mock private ClosedPositionRepository closedPositionRepository;
  @Mock private CurrencyRateRepository currencyRateRepository;
  @Mock private ImportRepository importRepository;
  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AccountDailyRepository accountDailyRepository;
  @Mock private AccountStatisticsRepository accountStatisticsRepository;

  @Test
  void resetImportedDataClearsImportedRowsProjectionsAndFx() {
    when(cashOperationRepository.count()).thenReturn(2L);
    when(closedPositionRepository.count()).thenReturn(3L);
    when(openedPositionRepository.count()).thenReturn(4L);
    when(importRepository.count()).thenReturn(5L);
    when(currencyRateRepository.count()).thenReturn(6L);

    DatabaseResetService service =
        new DatabaseResetService(
            cashOperationRepository,
            closedPositionRepository,
            currencyRateRepository,
            importRepository,
            openedPositionRepository,
            accountDailyRepository,
            accountStatisticsRepository);

    String message = service.resetImportedData();

    assertEquals(
        "Reset imported data: 2 cash, 3 closed, 4 open, 5 imports, 6 FX rows removed", message);

    InOrder deletes =
        inOrder(
            accountDailyRepository,
            accountStatisticsRepository,
            cashOperationRepository,
            closedPositionRepository,
            openedPositionRepository,
            importRepository,
            currencyRateRepository);
    deletes.verify(accountDailyRepository).deleteAllInBatch();
    deletes.verify(accountStatisticsRepository).deleteAllInBatch();
    deletes.verify(cashOperationRepository).deleteAllInBatch();
    deletes.verify(closedPositionRepository).deleteAllInBatch();
    deletes.verify(openedPositionRepository).deleteAllInBatch();
    deletes.verify(importRepository).deleteAllInBatch();
    deletes.verify(currencyRateRepository).deleteAllInBatch();
  }
}
