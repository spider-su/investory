package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class XtbImportServiceTest {

    @Mock
    private ClosedPositionRepository closedPositionRepository;
    @Mock
    private OpenedPositionRepository openedPositionRepository;
    @Mock
    private CashOperationRepository cashOperationRepository;
    @Mock
    private AccountSummaryRepository accountSummaryRepository;

    @InjectMocks
    private XtbImportService xtbImportService;

    @Test
    void importXtbExport_parsesA1WorkbookAndPersistsAllSections() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/a1.xlsx")) {
            assertNotNull(inputStream, "Test fixture /a1.xlsx must exist on classpath");

            xtbImportService.importXtbExport(inputStream);
        }

        ArgumentCaptor<Iterable<CashOperation>> cashCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<ClosedPosition>> closedCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<List<OpenedPosition>> openedCaptor = ArgumentCaptor.forClass(List.class);

        verify(cashOperationRepository).saveAll(cashCaptor.capture());
        verify(closedPositionRepository).saveAll(closedCaptor.capture());
        verify(openedPositionRepository).removeAllByAccountNotIn(eq("51499241"), openedCaptor.capture());
        verify(openedPositionRepository).saveAll(anyList());

        List<CashOperation> cashOperations = toList(cashCaptor.getValue());
        List<ClosedPosition> closedPositions = toList(closedCaptor.getValue());
        List<OpenedPosition> openedPositions = openedCaptor.getValue();

        assertFalse(cashOperations.isEmpty(), "Cash operation section should be imported");
        assertFalse(closedPositions.isEmpty(), "Closed position section should be imported");
        assertFalse(openedPositions.isEmpty(), "Open position section should be imported");

        assertTrue(cashOperations.stream().allMatch(c -> "51499241".equals(c.getAccount())));
        assertTrue(closedPositions.stream().allMatch(c -> "51499241".equals(c.getAccount())));
        assertTrue(openedPositions.stream().allMatch(c -> "51499241".equals(c.getAccount())));

        assertTrue(openedPositions.stream().allMatch(c -> c.getCurrency() == CurrencyType.USD));
        assertTrue(closedPositions.stream().allMatch(c -> c.getCurrency() == CurrencyType.USD));
        assertTrue(cashOperations.stream().allMatch(c -> c.getCurrency() == CurrencyType.USD));

        assertTrue(openedPositions.stream().anyMatch(p -> "AAPL.US".equals(p.getSymbol())));
        assertTrue(closedPositions.stream().anyMatch(p -> "HOOD.US".equals(p.getSymbol())));
        assertTrue(cashOperations.stream().anyMatch(c -> "AVGO.US".equals(c.getSymbol())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/a2.xlsx",
            "/a3.xlsx",
            "/b1.xlsx",
            "/b2.xlsx",
            "/b3.xlsx",
            "/eu.xlsx",
            "/us1.xlsx",
            "/us2.xlsx",
            "/account_51499241_en_xlsx_2026-04-30_2026-05-31.xlsx",
            "/account_51993106_en_xlsx_2026-04-30_2026-05-31.xlsx"
    })
    void importXtbExport_handlesAllOtherFixtures(String resourcePath) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream, "Test fixture must exist: " + resourcePath);
            assertDoesNotThrow(() -> xtbImportService.importXtbExport(inputStream));
        }

        verify(cashOperationRepository, atLeastOnce()).saveAll(anyList());
        verify(closedPositionRepository, atLeastOnce()).saveAll(anyList());
        verify(openedPositionRepository, atLeastOnce()).saveAll(anyList());
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T item : iterable) {
            list.add(item);
        }
        return list;
    }
}

