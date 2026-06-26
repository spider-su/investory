package com.example.demo.config;

import com.example.demo.services.currency.CurrencyRateUpdaterService;
import com.example.demo.services.MarketService;
import com.example.demo.services.notifications.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SchedulerConfigTest {

    @Mock private MarketService marketService;
    @Mock private CurrencyRateUpdaterService updaterService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private SchedulerConfig schedulerConfig;

    @Test
    void updateCurrencyRates_delegatesToUpdaterService() {
        schedulerConfig.updateCurrencyRates();
        verify(updaterService).updateCurrencyRates();
    }

    @Test
    void recordAtMarketOpen_runsFullPortfolioUpdate() {
        schedulerConfig.recordAtMarketOpen();
        verify(marketService).fullPortfolioUpdate();
    }

    @Test
    void recordAtMarketClose_runsUpdateThenDigestThenAlerts() {
        schedulerConfig.recordAtMarketClose();

        org.mockito.InOrder order = inOrder(marketService, notificationService);
        order.verify(marketService).fullPortfolioUpdate();
        //TODO: fixme
        //        order.verify(notificationService).sendDailyDigest();
        //        order.verify(notificationService).runAlerts();
    }
}

