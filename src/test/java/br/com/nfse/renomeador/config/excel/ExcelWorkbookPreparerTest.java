package br.com.nfse.renomeador.config.excel;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelWorkbookPreparerTest {
    @TempDir
    Path tempDir;

    @Test
    void preparesDashboardWorkbookForRestOperation() throws Exception {
        Path input = tempDir.resolve("entrada.xlsm");
        Path output = tempDir.resolve("saida.xlsm");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(input)) {
            Sheet sheet = workbook.createSheet("Dashboard Fiscal");
            sheet.createRow(0).createCell(0).setCellValue("DASHBOARD FISCAL");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(2).setCellValue("EMPRESA");
            header.createCell(17).setCellValue("CAMINHO REST\n(duplo-clique)");
            Row valid = sheet.createRow(2);
            valid.createCell(0).setCellValue("Cliente Valido");
            valid.createCell(3).setCellValue("25.014.360/0001-73");
            valid.createCell(17).setCellValue("/dados/clientes/dga");
            Row invalid = sheet.createRow(3);
            invalid.createCell(0).setCellValue("Cliente Invalido");
            invalid.createCell(3).setCellValue("123");
            workbook.write(out);
        }

        new ExcelWorkbookPreparer().prepare(input, output);

        try (XSSFWorkbook prepared = new XSSFWorkbook(Files.newInputStream(output))) {
            var sheet = prepared.getSheet("Dashboard Fiscal");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("PROTONS");
            assertThat(sheet.isDisplayGridlines()).isFalse();
            assertThat(fillArgb(sheet, 0, 0)).isEqualTo("FF07111F");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("CIDADE");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A2:S34");
            assertThat(sheet.getLastRowNum()).isEqualTo(33);
            assertThat(sheet.getRow(1).getCell(17).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(fillArgb(sheet, 1, 17)).isEqualTo("FF00B4D8");
            assertThat(sheet.getRow(2).getCell(17).getStringCellValue()).isEqualTo("/dados/clientes/dga");
            assertThat(fillArgb(sheet, 2, 17)).isEqualTo("FFE3FFF4");
            assertThat(sheet.getRow(33).getCell(3).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getRow(33).getCell(17).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(fillArgb(sheet, 2, 3)).isNotEqualTo("FFFFF2B8");
            assertThat(fillArgb(sheet, 3, 3)).isEqualTo("FFFFF2B8");
            assertThat(sheet.getRow(3).getCell(3).getCellComment()).isNotNull();
        }
    }

    @Test
    void canPrepareWorkbookThatAlreadyHasInvalidCnpjComments() throws Exception {
        Path input = tempDir.resolve("entrada-com-comentario.xlsm");
        Path first = tempDir.resolve("primeira.xlsm");
        Path second = tempDir.resolve("segunda.xlsm");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(input)) {
            Sheet sheet = workbook.createSheet("Dashboard Fiscal");
            sheet.createRow(0).createCell(0).setCellValue("DASHBOARD FISCAL");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(2).setCellValue("EMPRESA");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(17).setCellValue("CAMINHO REST\n(duplo-clique)");
            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("Cliente Invalido");
            invalid.createCell(3).setCellValue("123");
            workbook.write(out);
        }

        ExcelWorkbookPreparer preparer = new ExcelWorkbookPreparer();
        preparer.prepare(input, first);
        preparer.prepare(first, second);

        try (XSSFWorkbook prepared = new XSSFWorkbook(Files.newInputStream(second))) {
            assertThat(prepared.getSheet("Dashboard Fiscal").getRow(2).getCell(3).getCellComment()).isNotNull();
        }
    }

    private static String fillArgb(Sheet sheet, int row, int column) {
        XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(row).getCell(column).getCellStyle();
        return style.getFillForegroundXSSFColor().getARGBHex();
    }
}
