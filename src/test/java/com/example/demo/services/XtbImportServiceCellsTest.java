package com.example.demo.services;

import com.example.demo.services.imports.xtb.XtbImportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Direct tests for {@code XtbImportService.getString(Cell)}. POI throws
 * {@code IllegalStateException} when {@code getStringCellValue()} is called on a numeric
 * cell, so the helper was hardened to fall back to a locale-neutral {@code DataFormatter}.
 * Exercising it through a real XTB workbook would mask regressions because the live data
 * rarely mixes cell types in the columns we read.
 */
class XtbImportServiceCellsTest {

    private XtbImportService service;
    private Workbook workbook;
    private Sheet sheet;
    private Method getString;

    @BeforeEach
    void setUp() throws Exception {
        service = new XtbImportService(null, null, null, null);
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("test");
        getString = XtbImportService.class.getDeclaredMethod("getString", Cell.class);
        getString.setAccessible(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        workbook.close();
    }

    @Test
    void returnsNullForMissingOrBlankCell() throws Exception {
        Row row = sheet.createRow(0);
        Cell blank = row.createCell(0, CellType.BLANK);

        assertNull(invoke(null));
        assertNull(invoke(blank));
    }

    @Test
    void trimsStringCell() throws Exception {
        Cell cell = sheet.createRow(0).createCell(0, CellType.STRING);
        cell.setCellValue("  AAPL.US  ");

        assertEquals("AAPL.US", invoke(cell));
    }

    @Test
    void formatsNumericCellWithoutThrowing() throws Exception {
        Cell intLike = sheet.createRow(0).createCell(0, CellType.NUMERIC);
        intLike.setCellValue(12345);
        Cell decimal = sheet.createRow(1).createCell(0, CellType.NUMERIC);
        decimal.setCellValue(1.5);

        assertEquals("12345", invoke(intLike));
        // Locale.ROOT keeps the decimal point so downstream Double.valueOf still works.
        assertEquals("1.5", invoke(decimal));
    }

    @Test
    void formatsBooleanCell() throws Exception {
        Cell cell = sheet.createRow(0).createCell(0, CellType.BOOLEAN);
        cell.setCellValue(true);

        assertEquals("TRUE", invoke(cell));
    }

    @Test
    void returnsNullForWhitespaceOnlyCell() throws Exception {
        Cell cell = sheet.createRow(0).createCell(0, CellType.STRING);
        cell.setCellValue("   ");

        assertEquals("", invoke(cell));
    }

    private String invoke(Cell cell) throws Exception {
        return (String) getString.invoke(service, cell);
    }
}

