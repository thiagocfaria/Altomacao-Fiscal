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
import java.util.ArrayList;
import java.util.List;

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
            header.createCell(18).setCellValue("CAMINHO CERTIFICADO DIGITAL\n(FUTURO)");
            header.createCell(19).setCellValue("SOMENTE ORIGEM");
            header.createCell(30).setCellValue("RANK CERTIFICADO LEGADO");
            for (int column = 20; column <= 23; column++) {
                sheet.setColumnHidden(column, true);
            }
            sheet.setColumnHidden(30, true);
            Row valid = sheet.createRow(2);
            valid.createCell(0).setCellValue("Cliente Valido");
            valid.createCell(3).setCellValue("25.014.360/0001-73");
            valid.createCell(17).setCellValue("/dados/clientes/dga");
            valid.createCell(18).setCellValue("/certificados/dga.pfx");
            valid.createCell(19).setCellValue("SIM");
            valid.createCell(30).setCellFormula("RANK(AC3,$AC$3:$AC$34,1)");
            valid.setZeroHeight(true);
            Row invalid = sheet.createRow(3);
            invalid.createCell(0).setCellValue("Cliente Invalido");
            invalid.createCell(3).setCellValue("123");
            invalid.createCell(17).setCellValue("/dados/clientes/origem");
            invalid.setZeroHeight(true);
            workbook.write(out);
        }

        new ExcelWorkbookPreparer().prepare(input, output);

        try (XSSFWorkbook prepared = new XSSFWorkbook(Files.newInputStream(output))) {
            assertThat(prepared.getSheetAt(0).getSheetName()).isEqualTo("DASHBOARD");
            assertThat(prepared.getSheetAt(1).getSheetName()).isEqualTo("CADASTRO ABRIL");
            assertThat(prepared.getSheetAt(2).getSheetName()).isEqualTo("CADASTRO MAIO");
            assertThat(prepared.getSheetAt(9).getSheetName()).isEqualTo("CADASTRO DEZEMBRO");
            assertThat(prepared.getSheetAt(10).getSheetName()).isEqualTo("CONFIG");

            var dashboard = prepared.getSheet("DASHBOARD");
            assertThat(dashboard.getRow(0).getCell(0).getStringCellValue()).isEqualTo("NFSE");
            assertThat(dashboard.getRow(0).getCell(2).getStringCellValue())
                    .isEqualTo("PAINEL CONTÁBIL | AUTOMAÇÃO FISCAL");
            assertThat(dashboard.isDisplayGridlines()).isFalse();
            assertThat(fillArgb(dashboard, 0, 0)).isEqualTo("FF071E36");
            assertThat(fillArgb(dashboard, 33, 18)).isEqualTo("FF061A2F");
            assertThat(fillArgb(dashboard, 33, 25)).isEqualTo("FF111827");
            assertThat(fillArgb(dashboard, 59, 33)).isEqualTo("FF111827");
            assertThat(dashboard.getColumnWidth(18)).isGreaterThan(10 * 256);
            assertThat(dashboard.getColumnWidth(33)).isGreaterThan(10 * 256);
            assertThat(fontSize(dashboard, 6, 0)).isGreaterThanOrEqualTo((short) 20);
            assertThat(fontSize(dashboard, 6, 5)).isGreaterThanOrEqualTo((short) 20);
            assertThat(fontSize(dashboard, 6, 10)).isGreaterThanOrEqualTo((short) 20);
            assertThat(fontSize(dashboard, 6, 15)).isGreaterThanOrEqualTo((short) 20);
            assertThat(fontArgb(dashboard, 6, 15)).isEqualTo("FF10B981");
            assertThat(fontArgb(dashboard, 5, 16)).isEqualTo("FF10B981");
            assertThat(prepared.getForceFormulaRecalculation()).isTrue();
            assertThat(dashboard.getRow(6).getCell(1).getCellFormula()).isEqualTo("COUNTA('CADASTRO MAIO'!$A$3:$A$34)");
            assertThat(dashboard.getRow(6).getCell(6).getCellFormula()).isEqualTo("CONFIG!$B$10");
            assertThat(dashboard.getRow(6).getCell(11).getCellFormula()).isEqualTo("CONFIG!$B$11");
            assertThat(dashboard.getRow(6).getCell(16).getCellFormula())
                    .isEqualTo("IF(COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<15\")>0,COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<15\"),COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<=30\"))");
            assertThat(dashboard.getRow(8).getCell(16).getCellFormula())
                    .isEqualTo("IF(COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<15\")>0,\"VENCEM EM MENOS DE 15 DIAS\",IF(COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<=30\")>0,\"VENCEM EM ATE 30 DIAS\",\"SEM CERTIFICADO PERTO\"))");
            assertThat(conditionalFormattingFormulas(dashboard))
                    .contains("COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<15\")>0")
                    .contains("AND(COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<15\")=0,COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<=30\")>0)")
                    .contains("COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$U$3:$U$34,\"<>\",'CADASTRO MAIO'!$AB$3:$AB$34,\"<=30\")=0");
            assertThat(dashboard.getRow(11).getCell(0).getStringCellValue())
                    .isEqualTo("CERTIFICADOS DIGITAIS MAIS PRÓXIMOS DE VENCER");
            assertThat(dashboard.getRow(13).getCell(13).getStringCellValue()).isEqualTo("FALTA VENCER");
            assertThat(dashboard.getRow(14).getCell(0).getCellFormula())
                    .isEqualTo("IFERROR(INDEX('CADASTRO MAIO'!$A$3:$A$34,MATCH(1,'CADASTRO MAIO'!$AD$3:$AD$34,0)),\"\")");
            assertThat(dashboard.getRow(14).getCell(13).getCellFormula()).contains("FALTAM ");
            assertThat(dashboard.getRow(14).getCell(17).getCellFormula())
                    .isEqualTo("IFERROR(INDEX('CADASTRO MAIO'!$AC$3:$AC$34,MATCH(1,'CADASTRO MAIO'!$AD$3:$AD$34,0)),\"\")");
            assertThat(dashboard.getRow(20).getCell(0).getStringCellValue()).isEqualTo("SAÚDE DO CADASTRO OPERACIONAL");
            assertThat(dashboard.getRow(22).getCell(0).getStringCellValue()).isEqualTo("REST PENDENTE");
            assertThat(dashboard.getRow(22).getCell(5).getStringCellValue()).isEqualTo("CNPJ INVÁLIDO");
            assertThat(dashboard.getRow(22).getCell(10).getStringCellValue()).isEqualTo("CERT. SEM DATA");
            assertThat(dashboard.getRow(22).getCell(15).getStringCellValue()).isEqualTo("CERT. SEM CAMINHO");
            assertThat(dashboard.getRow(23).getCell(0).getCellFormula())
                    .isEqualTo("COUNTIFS('CADASTRO MAIO'!$A$3:$A$34,\"<>\",'CADASTRO MAIO'!$R$3:$R$34,\"\")");
            assertThat(dashboard.getRow(22).getCell(0).getStringCellValue()).doesNotContain("Entradas");
            assertThat(dashboard.getRow(22).getCell(0).getStringCellValue()).doesNotContain("Saidas");
            assertThat(dashboard.getSheetConditionalFormatting().getNumConditionalFormattings()).isGreaterThan(0);

            var sheet = prepared.getSheet("CADASTRO ABRIL");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("CADASTRO OPERACIONAL | RENOMEADOR NFS-e");
            assertThat(sheet.isDisplayGridlines()).isFalse();
            assertThat(fillArgb(sheet, 0, 0)).isEqualTo("FF07111F");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("CIDADE");
            assertThat(sheet.getRow(1).getCell(18).getStringCellValue())
                    .isEqualTo("CAMINHO ENTRADA/SAIDA\n(DUPLO-CLIQUE)");
            assertThat(sheet.getRow(1).getCell(19).getStringCellValue())
                    .isEqualTo("CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)");
            assertThat(sheet.getRow(1).getCell(20).getStringCellValue())
                    .isEqualTo("VALIDADE CERTIFICADO DIGITAL");
            assertThat(sheet.getRow(1).getCell(21).getStringCellValue())
                    .isEqualTo("SENHA CERTIFICADO DIGITAL\n(OPCIONAL)");
            assertThat(sheet.getRow(1).getCell(22).getStringCellValue()).isEqualTo("SOMENTE ORIGEM");
            assertThat(sheet.getRow(1).getCell(31).getStringCellValue()).isEqualTo("IMPORT API PN ATIVO");
            assertThat(sheet.getRow(1).getCell(32).getStringCellValue()).isEqualTo("CERTIFICADO API PN PASTA");
            assertThat(sheet.getRow(1).getCell(33).getStringCellValue()).isEqualTo("CERTIFICADO API PN ARQUIVO");
            assertThat(sheet.getRow(1).getCell(34).getStringCellValue()).isEqualTo("CERTIFICADO API PN ALIAS");
            assertThat(sheet.getRow(1).getCell(35).getStringCellValue()).isEqualTo("VALIDADE CERTIFICADO API PN");
            assertThat(sheet.getRow(1).getCell(36).getStringCellValue()).isEqualTo("CNPJ RAIZ CERTIFICADO");
            assertThat(sheet.getRow(1).getCell(37).getStringCellValue()).isEqualTo("MODO API PN");
            assertThat(sheet.getRow(1).getCell(38).getStringCellValue()).isEqualTo("AMBIENTE API PN");
            assertThat(sheet.getRow(1).getCell(39).getStringCellValue()).isEqualTo("STATUS API PN");
            assertThat(sheet.getRow(1).getCell(40).getStringCellValue()).isEqualTo("ULTIMO NSU API PN");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A2:AO34");
            assertThat(sheet.getLastRowNum()).isEqualTo(33);
            assertThat(sheet.getRow(1).getCell(17).getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(fillArgb(sheet, 1, 17)).isEqualTo("FF00B4D8");
            assertThat(sheet.getRow(2).getCell(17).getStringCellValue()).isEqualTo("/dados/clientes/dga");
            assertThat(sheet.getRow(2).getCell(19).getStringCellValue()).isEqualTo("/certificados/dga.pfx");
            assertThat(sheet.getRow(2).getCell(22).toString()).isBlank();
            assertThat(sheet.isColumnHidden(19)).isFalse();
            assertThat(sheet.isColumnHidden(20)).isFalse();
            assertThat(sheet.isColumnHidden(21)).isFalse();
            assertThat(sheet.isColumnHidden(22)).isFalse();
            assertThat(sheet.isColumnHidden(31)).isFalse();
            assertThat(sheet.isColumnHidden(40)).isFalse();
            assertThat(sheet.getRow(2).getZeroHeight()).isFalse();
            assertThat(sheet.getRow(3).getZeroHeight()).isFalse();
            assertThat(fillArgb(sheet, 2, 17)).isEqualTo("FFE3FFF4");
            assertThat(sheet.getRow(33).getCell(3).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getRow(33).getCell(17).getCellStyle().getDataFormatString()).isEqualTo("@");
            assertThat(sheet.getRow(33).getCell(20).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(sheet.getRow(33).getCell(35).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(fillArgb(sheet, 2, 3)).isNotEqualTo("FFFFF2B8");
            assertThat(fillArgb(sheet, 3, 3)).isEqualTo("FFFFF2B8");
            assertThat(sheet.getRow(3).getCell(3).getCellComment()).isNotNull();
            assertThat(sheet.getRow(3).getCell(22).getStringCellValue()).isEqualTo("SIM");
            assertThat(sheet.isColumnHidden(23)).isTrue();
            assertThat(sheet.isColumnHidden(29)).isTrue();
            assertThat(sheet.getRow(2).getCell(23).getCellFormula()).contains("CNPJ INVALIDO");
            assertThat(sheet.getRow(2).getCell(27).getCellFormula()).contains("TODAY()");
            assertThat(sheet.getRow(2).getCell(28).getCellFormula()).contains("AB3<15");
            assertThat(sheet.getRow(2).getCell(28).getCellFormula()).contains("AB3<=30");
            assertThat(sheet.getRow(2).getCell(28).getCellFormula()).doesNotContain("AB3<10");
            assertThat(sheet.getRow(2).getCell(29).getCellFormula()).contains("RANK(");
            assertThat(sheet.getRow(1).getCell(30).toString()).isBlank();
            assertThat(sheet.getRow(2).getCell(30).toString()).isBlank();
            assertThat(sheet.getRow(2).getCell(31).toString()).isBlank();
            assertThat(sheet.getRow(2).getCell(40).toString()).isBlank();

            var may = prepared.getSheet("CADASTRO MAIO");
            assertThat(may.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Cliente Valido");
            assertThat(may.getRow(2).getCell(3).getStringCellValue()).isEqualTo("25.014.360/0001-73");
            assertThat(may.getRow(2).getCell(16).getStringCellValue()).isBlank();
            assertThat(may.getRow(2).getCell(17).getStringCellValue()).isBlank();
            assertThat(may.getRow(2).getCell(18).getStringCellValue()).isBlank();
            assertThat(may.getRow(2).getCell(19).getStringCellValue()).isEqualTo("/certificados/dga.pfx");
            assertThat(may.getRow(2).getCell(22).toString()).isBlank();
            assertThat(may.getRow(2).getCell(30).toString()).isBlank();
            assertThat(may.getRow(2).getCell(31).toString()).isBlank();
            assertThat(may.getRow(2).getCell(40).toString()).isBlank();

            var config = prepared.getSheet("CONFIG");
            assertThat(config.getRow(0).getCell(0).getStringCellValue()).isEqualTo("CONFIGURACAO VISUAL E VALIDACOES");
            assertThat(config.getRow(2).getCell(0).getStringCellValue()).isEqualTo("SIM");
            assertThat(config.getRow(2).getCell(1).getStringCellValue())
                    .isEqualTo("Marca pasta REST monitorada apenas como origem");
            assertThat(config.getRow(9).getCell(0).getStringCellValue()).isEqualTo("NOTAS_IMPORTADAS_HOJE");
            assertThat(config.getRow(9).getCell(1).getNumericCellValue()).isZero();
            assertThat(config.getRow(10).getCell(0).getStringCellValue()).isEqualTo("XML_IMPORTADOS_HOJE");
            assertThat(config.getRow(10).getCell(1).getNumericCellValue()).isZero();
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
            assertThat(prepared.getSheet("CADASTRO ABRIL").getRow(2).getCell(3).getCellComment()).isNotNull();
            assertThat(prepared.getSheetAt(0).getSheetName()).isEqualTo("DASHBOARD");
        }
    }

    @Test
    void keepsValidNumericCnpjAsNormalClientRow() throws Exception {
        Path input = tempDir.resolve("entrada-cnpj-numerico.xlsm");
        Path output = tempDir.resolve("saida-cnpj-numerico.xlsm");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(input)) {
            Sheet sheet = workbook.createSheet("Dashboard Fiscal");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(17).setCellValue("CAMINHO REST");
            header.createCell(19).setCellValue("SOMENTE ORIGEM");

            Row valid = sheet.createRow(2);
            valid.createCell(0).setCellValue("Cliente Numerico");
            valid.createCell(3).setCellValue(25014360000173D);
            valid.createCell(17).setCellValue("/dados/clientes/numerico");
            valid.createCell(19).setCellValue("SIM");

            Row invalid = sheet.createRow(3);
            invalid.createCell(0).setCellValue("Cliente Invalido");
            invalid.createCell(3).setCellValue(123D);
            invalid.createCell(17).setCellValue("/dados/clientes/invalido");
            workbook.write(out);
        }

        new ExcelWorkbookPreparer().prepare(input, output);

        try (XSSFWorkbook prepared = new XSSFWorkbook(Files.newInputStream(output))) {
            Sheet sheet = prepared.getSheet("CADASTRO ABRIL");
            assertThat(fillArgb(sheet, 2, 3)).isNotEqualTo("FFFFF2B8");
            assertThat(sheet.getRow(2).getCell(3).getCellComment()).isNull();
            assertThat(sheet.getRow(2).getCell(22).toString()).isBlank();
            assertThat(fillArgb(sheet, 3, 3)).isEqualTo("FFFFF2B8");
            assertThat(sheet.getRow(2).getCell(23).getCellFormula())
                    .contains("TEXT(D3,\"00000000000000\")");
        }
    }

    private static String fillArgb(Sheet sheet, int row, int column) {
        XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(row).getCell(column).getCellStyle();
        return style.getFillForegroundXSSFColor().getARGBHex();
    }

    private static short fontSize(Sheet sheet, int row, int column) {
        XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(row).getCell(column).getCellStyle();
        return style.getFont().getFontHeightInPoints();
    }

    private static String fontArgb(Sheet sheet, int row, int column) {
        XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(row).getCell(column).getCellStyle();
        return style.getFont().getXSSFColor().getARGBHex();
    }

    private static List<String> conditionalFormattingFormulas(Sheet sheet) {
        var formulas = new ArrayList<String>();
        var formatting = sheet.getSheetConditionalFormatting();
        for (int index = 0; index < formatting.getNumConditionalFormattings(); index++) {
            var conditionalFormatting = formatting.getConditionalFormattingAt(index);
            for (int ruleIndex = 0; ruleIndex < conditionalFormatting.getNumberOfRules(); ruleIndex++) {
                formulas.add(conditionalFormatting.getRule(ruleIndex).getFormula1());
            }
        }
        return formulas;
    }
}
