package br.com.nfse.renomeador.config.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ExcelWorkbookPreparer {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("pt-BR"));
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");
    private static final String DASHBOARD_SHEET = "DASHBOARD";
    private static final String CADASTRO_SHEET = "CADASTRO";
    private static final String CONFIG_SHEET = "CONFIG";
    private static final String LEGACY_DASHBOARD_FISCAL_SHEET = "Dashboard Fiscal";
    private static final int HEADER_ROW_INDEX = 1;
    private static final int FIRST_DATA_ROW_INDEX = 2;
    private static final int CLIENT_COLUMN = 0;
    private static final int CNPJ_COLUMN = 3;
    private static final int DMS_COLUMN = 16;
    private static final int REST_COLUMN = 17;
    private static final int ENTRY_OUTPUT_COLUMN = 18;
    private static final int CERTIFICATE_PATH_COLUMN = 19;
    private static final int CERTIFICATE_EXPIRY_COLUMN = 20;
    private static final int CERTIFICATE_PASSWORD_COLUMN = 21;
    private static final int SOURCE_ONLY_COLUMN = 22;
    private static final int CNPJ_STATUS_COLUMN = 23;
    private static final int REST_STATUS_COLUMN = 24;
    private static final int DUPLICATE_STATUS_COLUMN = 25;
    private static final int PATH_STATUS_COLUMN = 26;
    private static final int CERTIFICATE_DAYS_COLUMN = 27;
    private static final int CERTIFICATE_STATUS_COLUMN = 28;
    private static final int CERTIFICATE_RANK_COLUMN = 29;
    private static final int LAST_HELPER_COLUMN = 29;
    private static final int LEGACY_LAST_HELPER_COLUMN = 30;
    private static final int API_PN_ACTIVE_COLUMN = 31;
    private static final int API_PN_CERTIFICATE_FOLDER_COLUMN = 32;
    private static final int API_PN_CERTIFICATE_FILE_COLUMN = 33;
    private static final int API_PN_CERTIFICATE_ALIAS_COLUMN = 34;
    private static final int API_PN_CERTIFICATE_EXPIRY_COLUMN = 35;
    private static final int API_PN_CERTIFICATE_ROOT_CNPJ_COLUMN = 36;
    private static final int API_PN_MODE_COLUMN = 37;
    private static final int API_PN_ENVIRONMENT_COLUMN = 38;
    private static final int API_PN_STATUS_COLUMN = 39;
    private static final int API_PN_LAST_NSU_COLUMN = 40;
    private static final int LAST_OPERATIONAL_COLUMN = API_PN_LAST_NSU_COLUMN;
    private static final int LEGACY_ENTRIES_COLUMN = 18;
    private static final int LEGACY_OUTPUTS_COLUMN = 19;
    private static final int LEGACY_CERTIFICATE_PATH_COLUMN = 20;
    private static final int LEGACY_CERTIFICATE_EXPIRY_COLUMN = 21;
    private static final int LEGACY_CERTIFICATE_PASSWORD_COLUMN = 22;
    private static final int LEGACY_SOURCE_ONLY_COLUMN = 23;
    private static final int DASHBOARD_LAST_COLUMN = 18;
    private static final int DASHBOARD_LAST_ROW = 33;
    private static final int DASHBOARD_CANVAS_LAST_COLUMN = 33;
    private static final int DASHBOARD_CANVAS_LAST_ROW = 59;
    private static final int EXTRA_READY_ROWS = 30;
    private static final List<Month> MONTHLY_CADASTRO_MONTHS = List.of(
            Month.APRIL, Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST,
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER);
    private static final DefaultIndexedColorMap COLOR_MAP = new DefaultIndexedColorMap();

    public void prepare(Path inputWorkbook, Path outputWorkbook) throws IOException {
        if (outputWorkbook.getParent() != null) {
            Files.createDirectories(outputWorkbook.getParent());
        }
        try (InputStream input = Files.newInputStream(inputWorkbook);
             Workbook workbook = WorkbookFactory.create(input);
             OutputStream output = Files.newOutputStream(outputWorkbook)) {
            Sheet cadastro = cadastroSheet(workbook);
            removeGeneratedSheet(workbook, DASHBOARD_SHEET, cadastro);
            removeGeneratedSheet(workbook, CONFIG_SHEET, cadastro);
            removeGeneratedSheet(workbook, LEGACY_DASHBOARD_FISCAL_SHEET, cadastro);
            removeMonthlySheets(workbook, cadastro);
            renameToCadastroMonth(workbook, cadastro, Month.APRIL);

            int lastReadyRow = prepareCadastroSheet(workbook, cadastro);
            List<Sheet> monthlySheets = createFutureMonthlySheets(workbook, cadastro, lastReadyRow);
            Sheet dashboard = workbook.createSheet(DASHBOARD_SHEET);
            String dashboardCadastro = activeCadastroSheetName(LocalDate.now());
            prepareDashboardSheet(workbook, dashboard, lastReadyRow, dashboardCadastro);
            Sheet config = workbook.createSheet(CONFIG_SHEET);
            prepareConfigSheet(workbook, config);

            workbook.setSheetOrder(DASHBOARD_SHEET, 0);
            int sheetOrder = 1;
            for (Sheet monthlySheet : monthlySheets) {
                workbook.setSheetOrder(monthlySheet.getSheetName(), sheetOrder++);
            }
            workbook.setSheetOrder(CONFIG_SHEET, sheetOrder);
            workbook.setActiveSheet(0);
            workbook.setSelectedTab(0);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
        }
    }

    private static Sheet cadastroSheet(Workbook workbook) {
        Sheet cadastro = workbook.getSheet(CADASTRO_SHEET);
        if (cadastro != null) {
            return cadastro;
        }
        Sheet april = workbook.getSheet(monthlyCadastroSheetName(Month.APRIL));
        if (april != null) {
            return april;
        }
        Sheet legacy = workbook.getSheet(LEGACY_DASHBOARD_FISCAL_SHEET);
        if (legacy != null) {
            return legacy;
        }
        if (workbook.getNumberOfSheets() == 0) {
            return workbook.createSheet(CADASTRO_SHEET);
        }
        return workbook.getSheetAt(0);
    }

    private static void removeGeneratedSheet(Workbook workbook, String sheetName, Sheet protectedSheet) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null && sheet != protectedSheet) {
            workbook.removeSheetAt(workbook.getSheetIndex(sheet));
        }
    }

    private static void removeMonthlySheets(Workbook workbook, Sheet protectedSheet) {
        for (Month month : MONTHLY_CADASTRO_MONTHS) {
            removeGeneratedSheet(workbook, monthlyCadastroSheetName(month), protectedSheet);
        }
    }

    private static void renameToCadastroMonth(Workbook workbook, Sheet sheet, Month month) {
        String monthlyName = monthlyCadastroSheetName(month);
        if (!monthlyName.equals(sheet.getSheetName())) {
            workbook.setSheetName(workbook.getSheetIndex(sheet), monthlyName);
        }
    }

    private static List<Sheet> createFutureMonthlySheets(Workbook workbook, Sheet april, int lastReadyRow) {
        List<Sheet> monthlySheets = new java.util.ArrayList<>();
        monthlySheets.add(april);
        for (Month month : MONTHLY_CADASTRO_MONTHS) {
            if (month == Month.APRIL) {
                continue;
            }
            Sheet clone = workbook.cloneSheet(workbook.getSheetIndex(april));
            workbook.setSheetName(workbook.getSheetIndex(clone), monthlyCadastroSheetName(month));
            clearMonthlyPaths(clone, lastReadyRow);
            styleOperationalRows(workbook, clone, lastReadyRow);
            monthlySheets.add(clone);
        }
        return List.copyOf(monthlySheets);
    }

    private static void clearMonthlyPaths(Sheet sheet, int lastReadyRow) {
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            clearCells(row, DMS_COLUMN, ENTRY_OUTPUT_COLUMN);
        }
    }

    private static int prepareCadastroSheet(Workbook workbook, Sheet sheet) {
        migrateGeneratedLayoutColumns(sheet);
        int lastDataRow = lastDataRow(sheet);
        int lastReadyRow = Math.max(HEADER_ROW_INDEX + EXTRA_READY_ROWS + 1, lastDataRow + EXTRA_READY_ROWS);
        migrateLegacySourceOnlyColumn(sheet, lastReadyRow);
        removeBlankRowsAfter(sheet, lastReadyRow);
        ensureOperationalRows(sheet, lastReadyRow);
        removeAllMergedRegions(sheet);
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setZoom(90);
        setAutoFilter(sheet, new CellRangeAddress(HEADER_ROW_INDEX, lastReadyRow,
                CLIENT_COLUMN, LAST_OPERATIONAL_COLUMN));
        sheet.setColumnWidth(CLIENT_COLUMN, 52 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 26 * 256);
        sheet.setColumnWidth(CNPJ_COLUMN, 20 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 20 * 256);
        sheet.setColumnWidth(DMS_COLUMN, 44 * 256);
        sheet.setColumnWidth(REST_COLUMN, 62 * 256);
        sheet.setColumnWidth(ENTRY_OUTPUT_COLUMN, 42 * 256);
        sheet.setColumnWidth(CERTIFICATE_PATH_COLUMN, 48 * 256);
        sheet.setColumnWidth(CERTIFICATE_EXPIRY_COLUMN, 18 * 256);
        sheet.setColumnWidth(CERTIFICATE_PASSWORD_COLUMN, 24 * 256);
        sheet.setColumnWidth(SOURCE_ONLY_COLUMN, 18 * 256);
        sheet.setColumnWidth(API_PN_ACTIVE_COLUMN, 18 * 256);
        sheet.setColumnWidth(API_PN_CERTIFICATE_FOLDER_COLUMN, 44 * 256);
        sheet.setColumnWidth(API_PN_CERTIFICATE_FILE_COLUMN, 34 * 256);
        sheet.setColumnWidth(API_PN_CERTIFICATE_ALIAS_COLUMN, 28 * 256);
        sheet.setColumnWidth(API_PN_CERTIFICATE_EXPIRY_COLUMN, 18 * 256);
        sheet.setColumnWidth(API_PN_CERTIFICATE_ROOT_CNPJ_COLUMN, 22 * 256);
        sheet.setColumnWidth(API_PN_MODE_COLUMN, 24 * 256);
        sheet.setColumnWidth(API_PN_ENVIRONMENT_COLUMN, 24 * 256);
        sheet.setColumnWidth(API_PN_STATUS_COLUMN, 28 * 256);
        sheet.setColumnWidth(API_PN_LAST_NSU_COLUMN, 20 * 256);
        for (int column = CLIENT_COLUMN; column <= LAST_OPERATIONAL_COLUMN; column++) {
            sheet.setColumnHidden(column, false);
        }
        for (int column = CNPJ_STATUS_COLUMN; column <= LAST_HELPER_COLUMN; column++) {
            sheet.setColumnWidth(column, 22 * 256);
            sheet.setColumnHidden(column, true);
        }

        styleCadastroTitle(workbook, sheet);
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header != null) {
            styleHeader(workbook, header);
        }
        styleOperationalRows(workbook, sheet, lastReadyRow);
        populateHelperFormulas(workbook, sheet, lastReadyRow);
        clearStaleLegacyHelperColumns(sheet, lastReadyRow);
        clearSourceOnlyForValidClients(sheet, lastReadyRow);
        markInvalidCnpj(workbook, sheet);
        return lastReadyRow;
    }

    private static void ensureOperationalRows(Sheet sheet, int lastReadyRow) {
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastReadyRow; rowIndex++) {
            if (sheet.getRow(rowIndex) == null) {
                sheet.createRow(rowIndex);
            }
        }
    }

    private static void prepareDashboardSheet(Workbook workbook, Sheet sheet, int lastReadyRow,
                                              String cadastroSheetName) {
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setZoom(90);
        for (int column = 0; column <= DASHBOARD_CANVAS_LAST_COLUMN; column++) {
            int width = column <= DASHBOARD_LAST_COLUMN ? dashboardColumnWidth(column) : 13;
            sheet.setColumnWidth(column, width * 256);
        }
        CellStyle canvasBackground = dashboardCanvasBackgroundStyle(workbook);
        for (int rowIndex = 0; rowIndex <= DASHBOARD_CANVAS_LAST_ROW; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            row.setHeightInPoints(rowIndex <= DASHBOARD_LAST_ROW ? dashboardRowHeight(rowIndex) : 18);
            for (int column = 0; column <= DASHBOARD_CANVAS_LAST_COLUMN; column++) {
                row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(canvasBackground);
            }
        }

        CellStyle background = dashboardBackgroundStyle(workbook);
        for (int rowIndex = 0; rowIndex <= DASHBOARD_LAST_ROW; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            row.setHeightInPoints(dashboardRowHeight(rowIndex));
            for (int column = 0; column <= DASHBOARD_LAST_COLUMN; column++) {
                row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(background);
            }
        }

        createDashboardHeader(workbook, sheet);
        createMetricCard(workbook, sheet, 0, "CLIENTES",
                "COUNTA(%s)".formatted(range(cadastroSheetName, "A", lastReadyRow)),
                "Total no cadastro", "22D3EE", "CLI");
        createMetricCard(workbook, sheet, 5, "NOTAS HOJE", "CONFIG!$B$10",
                "Importador NF", "10B981", "NF");
        createMetricCard(workbook, sheet, 10, "XML HOJE", "CONFIG!$B$11",
                "Importador XML", "38BDF8", "XML");
        createMetricCard(workbook, sheet, 15, "CERT. ALERTA",
                certificateAlertValueFormula(cadastroSheetName, lastReadyRow),
                "Sem certificado perto", "10B981", "CERT");
        sheet.getRow(8).getCell(16, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                .setCellFormula(certificateAlertNoteFormula(cadastroSheetName, lastReadyRow));
        addCertificateAlertMetricConditionalFormatting(sheet, cadastroSheetName, lastReadyRow);

        createCertificatePanel(workbook, sheet, lastReadyRow, cadastroSheetName);
        createCadastroHealth(workbook, sheet, lastReadyRow, cadastroSheetName);
        createDashboardFooter(workbook, sheet);
    }

    private static int dashboardColumnWidth(int column) {
        return column == 4 || column == 9 || column == 14 ? 2 : 13;
    }

    private static float dashboardRowHeight(int rowIndex) {
        if (rowIndex <= 1) {
            return 30;
        }
        if (rowIndex == 2) {
            return 22;
        }
        if (rowIndex == 3 || rowIndex == 4 || rowIndex == 10 || rowIndex == 19
                || rowIndex == 29 || rowIndex >= 31) {
            return 9;
        }
        if (rowIndex >= 5 && rowIndex <= 9) {
            return 29;
        }
        if (rowIndex == 11 || rowIndex == 20) {
            return 28;
        }
        if (rowIndex == 13 || rowIndex == 22) {
            return 24;
        }
        if ((rowIndex >= 14 && rowIndex <= 17) || (rowIndex >= 23 && rowIndex <= 27)) {
            return 26;
        }
        return 20;
    }

    private static void createDashboardHeader(Workbook workbook, Sheet sheet) {
        merge(sheet, 0, 3, 0, 1);
        Cell badge = sheet.getRow(0).getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        badge.setCellValue("NFSE");
        styleRegion(workbook, sheet, 0, 3, 0, 1, "071E36", "22D3EE", (short) 18, true);
        applyOuterBorder(workbook, sheet, 0, 3, 0, 1, "22D3EE", BorderStyle.MEDIUM);

        merge(sheet, 0, 1, 2, 16);
        Cell title = sheet.getRow(0).getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        title.setCellValue("PAINEL CONT\u00c1BIL | AUTOMA\u00c7\u00c3O FISCAL");
        styleRegion(workbook, sheet, 0, 1, 2, 16, "061A2F", "FFFFFF", (short) 24, true);

        merge(sheet, 2, 2, 2, 16);
        Cell subtitle = sheet.getRow(2).getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        subtitle.setCellValue("Clientes, importa\u00e7\u00f5es, XML e certificados digitais cr\u00edticos para a rotina fiscal");
        styleRegion(workbook, sheet, 2, 2, 2, 16, "061A2F", "B9D6EA", (short) 12, false);

        merge(sheet, 0, 3, 17, 18);
        Cell status = sheet.getRow(0).getCell(17, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        status.setCellValue("PAINEL\nATIVO");
        styleRegion(workbook, sheet, 0, 3, 17, 18, "071E36", "10B981", (short) 11, true);
        applyOuterBorder(workbook, sheet, 0, 3, 17, 18, "22D3EE", BorderStyle.THIN);

        styleRegion(workbook, sheet, 4, 4, 0, DASHBOARD_LAST_COLUMN, "0A2F52", "22D3EE", (short) 8, false);
    }

    private static void createMetricCard(Workbook workbook, Sheet sheet, int firstColumn,
                                         String label, String formula, String note, String accent, String badge) {
        int lastColumn = firstColumn + 3;
        styleRegion(workbook, sheet, 5, 9, firstColumn, lastColumn, "071E36", "EAF6FF", (short) 10, false);
        merge(sheet, 6, 8, firstColumn, firstColumn);
        Cell badgeCell = sheet.getRow(6).getCell(firstColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        badgeCell.setCellValue(badge);
        styleRegion(workbook, sheet, 6, 8, firstColumn, firstColumn, "08243E", accent, (short) 22, true);

        merge(sheet, 5, 5, firstColumn + 1, lastColumn);
        Cell labelCell = sheet.getRow(5).getCell(firstColumn + 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        labelCell.setCellValue(label);
        styleRegion(workbook, sheet, 5, 5, firstColumn + 1, lastColumn, "071E36", accent, (short) 11, true);

        merge(sheet, 6, 7, firstColumn + 1, lastColumn);
        Cell valueCell = sheet.getRow(6).getCell(firstColumn + 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        valueCell.setCellFormula(formula);
        styleRegion(workbook, sheet, 6, 7, firstColumn + 1, lastColumn, "071E36", "FFFFFF", (short) 30, true);

        merge(sheet, 8, 9, firstColumn + 1, lastColumn);
        Cell noteCell = sheet.getRow(8).getCell(firstColumn + 1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        noteCell.setCellValue(note);
        styleRegion(workbook, sheet, 8, 9, firstColumn + 1, lastColumn, "071E36", "B9D6EA", (short) 10, false);
        applyOuterBorder(workbook, sheet, 5, 9, firstColumn, lastColumn, accent, BorderStyle.MEDIUM);
    }

    private static void createCertificatePanel(Workbook workbook, Sheet sheet, int lastReadyRow,
                                               String cadastroSheetName) {
        styleRegion(workbook, sheet, 11, 18, 0, DASHBOARD_LAST_COLUMN, "071E36", "EAF6FF", (short) 10, false);
        applyOuterBorder(workbook, sheet, 11, 18, 0, DASHBOARD_LAST_COLUMN, "22D3EE", BorderStyle.MEDIUM);

        merge(sheet, 11, 11, 0, DASHBOARD_LAST_COLUMN);
        Cell title = sheet.getRow(11).getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        title.setCellValue("CERTIFICADOS DIGITAIS MAIS PR\u00d3XIMOS DE VENCER");
        styleRegion(workbook, sheet, 11, 11, 0, DASHBOARD_LAST_COLUMN, "071E36", "22D3EE", (short) 14, true);

        merge(sheet, 12, 12, 0, DASHBOARD_LAST_COLUMN);
        Cell subtitle = sheet.getRow(12).getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        subtitle.setCellValue("Empresas com certificado digital cadastrado e menor prazo de vencimento");
        styleRegion(workbook, sheet, 12, 12, 0, DASHBOARD_LAST_COLUMN, "071E36", "7FB2CC", (short) 9, false);

        merge(sheet, 13, 13, 0, 6);
        merge(sheet, 13, 13, 7, 9);
        merge(sheet, 13, 13, 10, 12);
        merge(sheet, 13, 13, 13, 16);
        merge(sheet, 13, 13, 17, 18);
        String[] headers = {"EMPRESA", "CNPJ", "VENCIMENTO", "FALTA VENCER", "STATUS"};
        int[] columns = {0, 7, 10, 13, 17};
        int[] lastColumns = {6, 9, 12, 16, 18};
        for (int index = 0; index < headers.length; index++) {
            Cell cell = sheet.getRow(13).getCell(columns[index], Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cell.setCellValue(headers[index]);
            styleRegion(workbook, sheet, 13, 13, columns[index], lastColumns[index],
                    "0A2F52", "FFFFFF", (short) 9, true);
        }

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle tableStyle = dashboardStyle(workbook, "0B2545", "EAF6FF", (short) 9, false);
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.cloneStyleFrom(tableStyle);
        dateStyle.setDataFormat(dataFormat.getFormat("dd/mm/yyyy"));

        for (int rowIndex = 14; rowIndex <= 16; rowIndex++) {
            int rank = rowIndex - 13;
            merge(sheet, rowIndex, rowIndex, 0, 6);
            merge(sheet, rowIndex, rowIndex, 7, 9);
            merge(sheet, rowIndex, rowIndex, 10, 12);
            merge(sheet, rowIndex, rowIndex, 13, 16);
            merge(sheet, rowIndex, rowIndex, 17, 18);
            applyStyleToRegion(sheet, rowIndex, rowIndex, 0, 6, tableStyle);
            applyStyleToRegion(sheet, rowIndex, rowIndex, 7, 9, tableStyle);
            applyStyleToRegion(sheet, rowIndex, rowIndex, 10, 12, dateStyle);
            applyStyleToRegion(sheet, rowIndex, rowIndex, 13, 16, tableStyle);
            applyStyleToRegion(sheet, rowIndex, rowIndex, 17, 18, tableStyle);
            formulaCell(sheet, rowIndex, 0, certificateIndexFormula(cadastroSheetName, "A", rank, lastReadyRow), tableStyle);
            formulaCell(sheet, rowIndex, 7, certificateIndexFormula(cadastroSheetName, "D", rank, lastReadyRow), tableStyle);
            formulaCell(sheet, rowIndex, 10, certificateIndexFormula(cadastroSheetName, "U", rank, lastReadyRow), dateStyle);
            formulaCell(sheet, rowIndex, 13, certificateDaysLabelFormula(cadastroSheetName, rank, lastReadyRow), tableStyle);
            formulaCell(sheet, rowIndex, 17, certificateIndexFormula(cadastroSheetName, "AC", rank, lastReadyRow), tableStyle);
        }
        addCertificateConditionalFormatting(sheet);
    }

    private static void createCadastroHealth(Workbook workbook, Sheet sheet, int lastReadyRow,
                                             String cadastroSheetName) {
        styleRegion(workbook, sheet, 20, 28, 0, DASHBOARD_LAST_COLUMN, "071E36", "EAF6FF", (short) 10, false);
        applyOuterBorder(workbook, sheet, 20, 28, 0, DASHBOARD_LAST_COLUMN, "22D3EE", BorderStyle.MEDIUM);
        merge(sheet, 20, 20, 0, DASHBOARD_LAST_COLUMN);
        Cell title = sheet.getRow(20).getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        title.setCellValue("SA\u00daDE DO CADASTRO OPERACIONAL");
        styleRegion(workbook, sheet, 20, 20, 0, DASHBOARD_LAST_COLUMN, "071E36", "22D3EE", (short) 14, true);
        merge(sheet, 21, 21, 0, DASHBOARD_LAST_COLUMN);
        Cell subtitle = sheet.getRow(21).getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        subtitle.setCellValue("Pontos que precisam de revis\u00e3o antes da automa\u00e7\u00e3o rodar 24/7");
        styleRegion(workbook, sheet, 21, 21, 0, DASHBOARD_LAST_COLUMN, "071E36", "7FB2CC", (short) 9, false);

        createAlertCard(workbook, sheet, 0, "REST PENDENTE",
                "COUNTIFS(%s,\"<>\",%s,\"\")"
                        .formatted(range(cadastroSheetName, "A", lastReadyRow),
                                range(cadastroSheetName, "R", lastReadyRow)));
        createAlertCard(workbook, sheet, 5, "CNPJ INV\u00c1LIDO",
                "COUNTIF(%s,\"CNPJ INVALIDO\")".formatted(range(cadastroSheetName, "X", lastReadyRow)));
        createAlertCard(workbook, sheet, 10, "CERT. SEM DATA",
                "COUNTIF(%s,\"SEM DATA\")".formatted(range(cadastroSheetName, "AC", lastReadyRow)));
        createAlertCard(workbook, sheet, 15, "CERT. SEM CAMINHO",
                "COUNTIF(%s,\"SEM CAMINHO\")".formatted(range(cadastroSheetName, "AC", lastReadyRow)));
        addHealthStatusConditionalFormatting(sheet);
    }

    private static void createAlertCard(Workbook workbook, Sheet sheet, int firstColumn, String label, String formula) {
        int lastColumn = firstColumn + 3;
        styleRegion(workbook, sheet, 22, 27, firstColumn, lastColumn, "0B2545", "EAF6FF", (short) 10, false);
        merge(sheet, 22, 22, firstColumn, lastColumn);
        merge(sheet, 23, 24, firstColumn, lastColumn);
        merge(sheet, 25, 25, firstColumn, lastColumn);
        merge(sheet, 26, 27, firstColumn, lastColumn);
        Cell labelCell = sheet.getRow(22).getCell(firstColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        labelCell.setCellValue(label);
        styleRegion(workbook, sheet, 22, 22, firstColumn, lastColumn, "0A2F52", "FFFFFF", (short) 9, true);
        Cell valueCell = sheet.getRow(23).getCell(firstColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        valueCell.setCellFormula(formula);
        styleRegion(workbook, sheet, 23, 24, firstColumn, lastColumn, "0B2545", "FFFFFF", (short) 22, true);
        Cell noteCell = sheet.getRow(25).getCell(firstColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        noteCell.setCellValue("PONTO DE ATEN\u00c7\u00c3O");
        styleRegion(workbook, sheet, 25, 25, firstColumn, lastColumn, "0B2545", "7FB2CC", (short) 8, false);
        Cell statusCell = sheet.getRow(26).getCell(firstColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        statusCell.setCellFormula("IF(%s24=0,\"OK\",\"REVISAR\")".formatted(columnName(firstColumn)));
        styleRegion(workbook, sheet, 26, 27, firstColumn, lastColumn, "071E36", "10B981", (short) 10, true);
        applyOuterBorder(workbook, sheet, 22, 27, firstColumn, lastColumn, "22D3EE", BorderStyle.THIN);
    }

    private static void createDashboardFooter(Workbook workbook, Sheet sheet) {
        styleRegion(workbook, sheet, 30, 30, 0, DASHBOARD_LAST_COLUMN, "0A2F52", "22D3EE", (short) 8, false);
        merge(sheet, 31, 31, 3, 15);
        Cell cell = sheet.getRow(31).getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue("DASHBOARD VISUAL | DADOS REAIS NO CADASTRO");
        styleRegion(workbook, sheet, 31, 31, 3, 15, "061A2F", "7FB2CC", (short) 8, false);
    }

    private static String certificateIndexFormula(String sheetName, String column, int rank, int lastReadyRow) {
        int lastExcelRow = lastReadyRow + 1;
        String sheetRef = sheetRef(sheetName);
        return "IFERROR(INDEX(%s$%s$3:$%s$%d,MATCH(%d,%s$AD$3:$AD$%d,0)),\"\")"
                .formatted(sheetRef, column, column, lastExcelRow, rank, sheetRef, lastExcelRow);
    }

    private static String certificateDaysLabelFormula(String sheetName, int rank, int lastReadyRow) {
        int lastExcelRow = lastReadyRow + 1;
        String sheetRef = sheetRef(sheetName);
        String index = "INDEX(%s$AB$3:$AB$%d,MATCH(%d,%s$AD$3:$AD$%d,0))"
                .formatted(sheetRef, lastExcelRow, rank, sheetRef, lastExcelRow);
        return "IFERROR(IF(%s<0,\"VENCIDO HA \"&ABS(%s)&\" DIAS\",\"FALTAM \"&%s&\" DIAS\"),\"\")"
                .formatted(index, index, index);
    }

    private static String certificateAlertValueFormula(String sheetName, int lastReadyRow) {
        String critical = certificateCriticalCountFormula(sheetName, lastReadyRow);
        String warning = certificateWarningCountFormula(sheetName, lastReadyRow);
        return "IF(%s>0,%s,%s)".formatted(critical, critical, warning);
    }

    private static String certificateAlertNoteFormula(String sheetName, int lastReadyRow) {
        String critical = certificateCriticalCountFormula(sheetName, lastReadyRow);
        String warning = certificateWarningCountFormula(sheetName, lastReadyRow);
        return "IF(%s>0,\"VENCEM EM MENOS DE 15 DIAS\",IF(%s>0,\"VENCEM EM ATE 30 DIAS\",\"SEM CERTIFICADO PERTO\"))"
                .formatted(critical, warning);
    }

    private static String certificateCriticalCountFormula(String sheetName, int lastReadyRow) {
        return certificateAlertCountFormula(sheetName, lastReadyRow, "<15");
    }

    private static String certificateWarningCountFormula(String sheetName, int lastReadyRow) {
        return certificateAlertCountFormula(sheetName, lastReadyRow, "<=30");
    }

    private static String certificateAlertCountFormula(String sheetName, int lastReadyRow, String daysCriterion) {
        return "COUNTIFS(%s,\"<>\",%s,\"<>\",%s,\"%s\")"
                .formatted(range(sheetName, "A", lastReadyRow), range(sheetName, "U", lastReadyRow),
                        range(sheetName, "AB", lastReadyRow), daysCriterion);
    }

    private static void formulaCell(Sheet sheet, int rowIndex, int column, String formula, CellStyle style) {
        Cell cell = sheet.getRow(rowIndex).getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    private static void styleRegion(Workbook workbook, Sheet sheet, int firstRow, int lastRow,
                                    int firstColumn, int lastColumn, String fill, String fontColor,
                                    short fontSize, boolean bold) {
        CellStyle style = dashboardStyle(workbook, fill, fontColor, fontSize, bold);
        applyStyleToRegion(sheet, firstRow, lastRow, firstColumn, lastColumn, style);
    }

    private static void applyStyleToRegion(Sheet sheet, int firstRow, int lastRow,
                                           int firstColumn, int lastColumn, CellStyle style) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            for (int column = firstColumn; column <= lastColumn; column++) {
                row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(style);
            }
        }
    }

    private static void applyOuterBorder(Workbook workbook, Sheet sheet, int firstRow, int lastRow,
                                         int firstColumn, int lastColumn, String rgbHex, BorderStyle border) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            for (int column = firstColumn; column <= lastColumn; column++) {
                Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                CellStyle style = workbook.createCellStyle();
                style.cloneStyleFrom(cell.getCellStyle());
                boolean edge = false;
                if (rowIndex == firstRow) {
                    style.setBorderTop(border);
                    edge = true;
                }
                if (rowIndex == lastRow) {
                    style.setBorderBottom(border);
                    edge = true;
                }
                if (column == firstColumn) {
                    style.setBorderLeft(border);
                    edge = true;
                }
                if (column == lastColumn) {
                    style.setBorderRight(border);
                    edge = true;
                }
                if (edge) {
                    setBorderColors(style, rgbHex, IndexedColors.AQUA);
                }
                cell.setCellStyle(style);
            }
        }
    }

    private static void addCertificateConditionalFormatting(Sheet sheet) {
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        ConditionalFormattingRule red = formatting.createConditionalFormattingRule("$R15=\"VERMELHO\"");
        PatternFormatting redPattern = red.createPatternFormatting();
        redPattern.setFillBackgroundColor(IndexedColors.RED.getIndex());
        redPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule yellow = formatting.createConditionalFormattingRule("$R15=\"AMARELO\"");
        PatternFormatting yellowPattern = yellow.createPatternFormatting();
        yellowPattern.setFillBackgroundColor(IndexedColors.YELLOW.getIndex());
        yellowPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule green = formatting.createConditionalFormattingRule("$R15=\"VERDE\"");
        PatternFormatting greenPattern = green.createPatternFormatting();
        greenPattern.setFillBackgroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
        greenPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        formatting.addConditionalFormatting(
                new CellRangeAddress[]{new CellRangeAddress(14, 16, 0, DASHBOARD_LAST_COLUMN)},
                new ConditionalFormattingRule[]{red, yellow, green});
    }

    private static void addCertificateAlertMetricConditionalFormatting(Sheet sheet, String cadastroSheetName,
                                                                       int lastReadyRow) {
        String critical = certificateCriticalCountFormula(cadastroSheetName, lastReadyRow);
        String warning = certificateWarningCountFormula(cadastroSheetName, lastReadyRow);
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        ConditionalFormattingRule red = formatting.createConditionalFormattingRule(critical + ">0");
        PatternFormatting redPattern = red.createPatternFormatting();
        redPattern.setFillBackgroundColor(IndexedColors.RED.getIndex());
        redPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule yellow = formatting.createConditionalFormattingRule(
                "AND(%s=0,%s>0)".formatted(critical, warning));
        PatternFormatting yellowPattern = yellow.createPatternFormatting();
        yellowPattern.setFillBackgroundColor(IndexedColors.YELLOW.getIndex());
        yellowPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule green = formatting.createConditionalFormattingRule(warning + "=0");
        PatternFormatting greenPattern = green.createPatternFormatting();
        greenPattern.setFillBackgroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
        greenPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        formatting.addConditionalFormatting(
                new CellRangeAddress[]{new CellRangeAddress(5, 9, 15, 18)},
                new ConditionalFormattingRule[]{red, yellow, green});
    }

    private static void addHealthStatusConditionalFormatting(Sheet sheet) {
        addStatusConditionalFormatting(sheet, 0);
        addStatusConditionalFormatting(sheet, 5);
        addStatusConditionalFormatting(sheet, 10);
        addStatusConditionalFormatting(sheet, 15);
    }

    private static void addStatusConditionalFormatting(Sheet sheet, int firstColumn) {
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        String statusCell = "$" + columnName(firstColumn) + "$27";
        ConditionalFormattingRule review = formatting.createConditionalFormattingRule(statusCell + "=\"REVISAR\"");
        PatternFormatting reviewPattern = review.createPatternFormatting();
        reviewPattern.setFillBackgroundColor(IndexedColors.ORANGE.getIndex());
        reviewPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        ConditionalFormattingRule ok = formatting.createConditionalFormattingRule(statusCell + "=\"OK\"");
        PatternFormatting okPattern = ok.createPatternFormatting();
        okPattern.setFillBackgroundColor(IndexedColors.BRIGHT_GREEN.getIndex());
        okPattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        formatting.addConditionalFormatting(
                new CellRangeAddress[]{new CellRangeAddress(26, 27, firstColumn, firstColumn + 3)},
                new ConditionalFormattingRule[]{review, ok});
    }

    private static String range(String sheetName, String column, int lastReadyRow) {
        return sheetRef(sheetName) + "$" + column + "$3:$" + column + "$" + (lastReadyRow + 1);
    }

    private static String sheetRef(String sheetName) {
        return "'" + sheetName.replace("'", "''") + "'!";
    }

    private static String activeCadastroSheetName(LocalDate date) {
        Month month = YearMonth.from(date).getMonth();
        if (MONTHLY_CADASTRO_MONTHS.contains(month)) {
            return monthlyCadastroSheetName(month);
        }
        return monthlyCadastroSheetName(Month.APRIL);
    }

    private static String monthlyCadastroSheetName(Month month) {
        return "CADASTRO " + monthName(month);
    }

    private static String monthName(Month month) {
        return switch (month) {
            case JANUARY -> "JANEIRO";
            case FEBRUARY -> "FEVEREIRO";
            case MARCH -> "MARCO";
            case APRIL -> "ABRIL";
            case MAY -> "MAIO";
            case JUNE -> "JUNHO";
            case JULY -> "JULHO";
            case AUGUST -> "AGOSTO";
            case SEPTEMBER -> "SETEMBRO";
            case OCTOBER -> "OUTUBRO";
            case NOVEMBER -> "NOVEMBRO";
            case DECEMBER -> "DEZEMBRO";
        };
    }

    private static void prepareConfigSheet(Workbook workbook, Sheet sheet) {
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 42 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        sheet.setColumnWidth(4, 24 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        merge(sheet, 0, 0, 0, 5);
        Row title = sheet.createRow(0);
        Cell titleCell = title.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        titleCell.setCellValue("CONFIGURACAO VISUAL E VALIDACOES");
        titleCell.setCellStyle(solidStyle(workbook, "061A2F", "FFFFFF", (short) 16, true));

        Row header = sheet.createRow(1);
        String[] headers = {"LISTA", "DESCRICAO", "USO", "", "PALETA", "HEX"};
        for (int column = 0; column < headers.length; column++) {
            Cell cell = header.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(solidStyle(workbook, "0A2F52", "22D3EE", (short) 10, true));
        }
        configRow(workbook, sheet, 2, "SIM", "Marca pasta REST monitorada apenas como origem", "SOMENTE ORIGEM");
        configRow(workbook, sheet, 3, "NAO", "Valor padrao quando a pasta tambem pode ser destino", "SOMENTE ORIGEM");
        configRow(workbook, sheet, 4, "A", "Prioridade herdada do cadastro", "PRIORIDADE");
        configRow(workbook, sheet, 5, "B", "Prioridade herdada do cadastro", "PRIORIDADE");
        configRow(workbook, sheet, 6, "C", "Prioridade herdada do cadastro", "PRIORIDADE");
        paletteRow(workbook, sheet, 2, "Azul profundo", "#061A2F");
        paletteRow(workbook, sheet, 3, "Azul painel", "#0B2545");
        paletteRow(workbook, sheet, 4, "Ciano destaque", "#22D3EE");
        paletteRow(workbook, sheet, 5, "Verde pronto", "#10B981");
        paletteRow(workbook, sheet, 6, "Amarelo alerta", "#F59E0B");
        Row operationHeader = sheet.createRow(8);
        String[] operationHeaders = {"INDICADOR", "VALOR", "USO"};
        for (int column = 0; column < operationHeaders.length; column++) {
            Cell cell = operationHeader.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cell.setCellValue(operationHeaders[column]);
            cell.setCellStyle(solidStyle(workbook, "0A2F52", "22D3EE", (short) 10, true));
        }
        operationCounterRow(workbook, sheet, 9, "NOTAS_IMPORTADAS_HOJE", "Atualizado pelo futuro importador de NF");
        operationCounterRow(workbook, sheet, 10, "XML_IMPORTADOS_HOJE", "Atualizado pelo futuro importador de XML");
    }

    private static void configRow(Workbook workbook, Sheet sheet, int rowIndex, String value, String description,
                                  String usage) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        CellStyle style = solidStyle(workbook, "F8FBFF", "0F172A", (short) 10, false);
        row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(value);
        row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(description);
        row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(usage);
        for (int column = 0; column <= 2; column++) {
            row.getCell(column).setCellStyle(style);
        }
    }

    private static void paletteRow(Workbook workbook, Sheet sheet, int rowIndex, String label, String hex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        CellStyle style = solidStyle(workbook, "F8FBFF", "0F172A", (short) 10, false);
        row.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(label);
        row.getCell(5, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(hex);
        row.getCell(4).setCellStyle(style);
        row.getCell(5).setCellStyle(style);
    }

    private static void operationCounterRow(Workbook workbook, Sheet sheet, int rowIndex, String indicator,
                                            String usage) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        CellStyle style = solidStyle(workbook, "F8FBFF", "0F172A", (short) 10, false);
        row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(indicator);
        row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(0);
        row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(usage);
        for (int column = 0; column <= 2; column++) {
            row.getCell(column).setCellStyle(style);
        }
    }

    private static void migrateGeneratedLayoutColumns(Sheet sheet) {
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null) {
            return;
        }
        boolean compactCertificateInEntryColumn = compact(header.getCell(LEGACY_ENTRIES_COLUMN))
                .startsWith("CAMINHOCERTIFICADODIGITAL");
        boolean oldModernLayout = compactCertificateInEntryColumn
                || compact(header.getCell(LEGACY_OUTPUTS_COLUMN)).equals("SOMENTEORIGEM")
                || compact(header.getCell(LEGACY_OUTPUTS_COLUMN)).startsWith("CAMINHOSAIDAS")
                || compact(header.getCell(LEGACY_CERTIFICATE_PATH_COLUMN)).startsWith("CAMINHOCERTIFICADODIGITAL")
                || compact(header.getCell(LEGACY_CERTIFICATE_PATH_COLUMN)).equals("STATUSCNPJ");
        if (!oldModernLayout) {
            return;
        }
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String oldEntryPath = cellText(row.getCell(LEGACY_ENTRIES_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String oldOutputPath = cellText(row.getCell(LEGACY_OUTPUTS_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String oldCertificatePath = compactCertificateInEntryColumn
                    ? oldEntryPath
                    : cellText(row.getCell(LEGACY_CERTIFICATE_PATH_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String oldCertificateExpiry = cellText(row.getCell(LEGACY_CERTIFICATE_EXPIRY_COLUMN,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String oldCertificatePassword = cellText(row.getCell(LEGACY_CERTIFICATE_PASSWORD_COLUMN,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String oldSourceOnly = compactCertificateInEntryColumn
                    ? oldOutputPath
                    : cellText(row.getCell(LEGACY_SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
            String entryOutputPath = compactCertificateInEntryColumn ? "" : firstNonBlank(oldEntryPath, oldOutputPath);
            clearCells(row, ENTRY_OUTPUT_COLUMN, LEGACY_SOURCE_ONLY_COLUMN);
            if (!entryOutputPath.isBlank()) {
                row.getCell(ENTRY_OUTPUT_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue(entryOutputPath);
            }
            if (!oldCertificatePath.isBlank()) {
                row.getCell(CERTIFICATE_PATH_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue(oldCertificatePath);
            }
            if (!oldCertificateExpiry.isBlank()) {
                row.getCell(CERTIFICATE_EXPIRY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue(oldCertificateExpiry);
            }
            if (!oldCertificatePassword.isBlank()) {
                row.getCell(CERTIFICATE_PASSWORD_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue(oldCertificatePassword);
            }
            if (!oldSourceOnly.isBlank()) {
                row.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue(oldSourceOnly);
            }
        }
        clearCells(header, ENTRY_OUTPUT_COLUMN, LEGACY_SOURCE_ONLY_COLUMN);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static void clearCells(Row row, int firstColumn, int lastColumn) {
        for (int column = firstColumn; column <= lastColumn; column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cell.setBlank();
            cell.removeCellComment();
        }
    }

    private static String cellText(Cell cell) {
        return cell == null ? "" : cell.toString().strip();
    }

    private static int lastDataRow(Sheet sheet) {
        for (int rowIndex = sheet.getLastRowNum(); rowIndex > HEADER_ROW_INDEX; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && hasOperationalValue(row)) {
                return rowIndex;
            }
        }
        return HEADER_ROW_INDEX + 1;
    }

    private static boolean hasOperationalValue(Row row) {
        for (int column = CLIENT_COLUMN; column <= LAST_OPERATIONAL_COLUMN; column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !cell.toString().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void removeBlankRowsAfter(Sheet sheet, int lastReadyRow) {
        for (int rowIndex = sheet.getLastRowNum(); rowIndex > lastReadyRow; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && !hasOperationalValue(row)) {
                sheet.removeRow(row);
            }
        }
    }

    private static void migrateLegacySourceOnlyColumn(Sheet sheet, int lastReadyRow) {
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header == null || !compact(header.getCell(LEGACY_ENTRIES_COLUMN)).equals("SOMENTEORIGEM")) {
            return;
        }
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Cell legacy = row.getCell(LEGACY_ENTRIES_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Cell current = row.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (legacy != null && current.toString().isBlank()) {
                current.setCellValue(legacy.toString());
                legacy.setCellValue("");
            }
        }
    }

    private static String compact(Cell cell) {
        return cell == null ? "" : NON_ALNUM.matcher(cell.toString()).replaceAll("").toUpperCase(Locale.ROOT);
    }

    private static void removeAllMergedRegions(Sheet sheet) {
        for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
            sheet.removeMergedRegion(index);
        }
    }

    private static void setAutoFilter(Sheet sheet, CellRangeAddress range) {
        if (sheet instanceof XSSFSheet xssfSheet && xssfSheet.getCTWorksheet().isSetAutoFilter()) {
            xssfSheet.getCTWorksheet().unsetAutoFilter();
        }
        sheet.setAutoFilter(range);
    }

    private static void styleCadastroTitle(Workbook workbook, Sheet sheet) {
        Row title = sheet.getRow(0);
        if (title == null) {
            title = sheet.createRow(0);
        }
        title.setHeightInPoints(42);
        Cell cell = title.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue("CADASTRO OPERACIONAL | RENOMEADOR NFS-e");

        CellStyle style = workbook.createCellStyle();
        setFill(style, "07111F", IndexedColors.DARK_TEAL);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBottomBorder(style, BorderStyle.MEDIUM, "00B4D8", IndexedColors.AQUA);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 24);
        setFontColor(font, "EAFBFF", IndexedColors.WHITE);
        style.setFont(font);
        for (int column = CLIENT_COLUMN; column <= LAST_OPERATIONAL_COLUMN; column++) {
            title.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(style);
        }
        CellRangeAddress titleRange = new CellRangeAddress(0, 0, CLIENT_COLUMN, LAST_OPERATIONAL_COLUMN);
        if (!isMerged(sheet, titleRange)) {
            sheet.addMergedRegion(titleRange);
        }
    }

    private static boolean isMerged(Sheet sheet, CellRangeAddress range) {
        for (int index = 0; index < sheet.getNumMergedRegions(); index++) {
            if (sheet.getMergedRegion(index).formatAsString().equals(range.formatAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void styleHeader(Workbook workbook, Row header) {
        CellStyle defaultHeader = workbook.createCellStyle();
        setFill(defaultHeader, "0F172A", IndexedColors.BLUE_GREY);
        defaultHeader.setAlignment(HorizontalAlignment.CENTER);
        defaultHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        defaultHeader.setWrapText(true);
        defaultHeader.setBorderBottom(BorderStyle.THIN);
        setBottomBorder(defaultHeader, BorderStyle.THIN, "38BDF8", IndexedColors.AQUA);
        Font defaultFont = workbook.createFont();
        defaultFont.setBold(true);
        defaultFont.setFontHeightInPoints((short) 10);
        setFontColor(defaultFont, "EAFBFF", IndexedColors.WHITE);
        defaultHeader.setFont(defaultFont);
        header.setHeightInPoints(32);

        for (int column = CLIENT_COLUMN; column <= LAST_HELPER_COLUMN; column++) {
            header.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(defaultHeader);
        }
        header.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("CIDADE");

        Cell dms = header.getCell(DMS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle dmsStyle = workbook.createCellStyle();
        dmsStyle.cloneStyleFrom(defaultHeader);
        setFill(dmsStyle, "0EA5E9", IndexedColors.LIGHT_BLUE);
        dms.setCellStyle(dmsStyle);
        dms.setCellValue("CAMINHO DMS\n(DUPLO-CLIQUE)");

        Cell rest = header.getCell(REST_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle restStyle = workbook.createCellStyle();
        restStyle.cloneStyleFrom(defaultHeader);
        setFill(restStyle, "00B4D8", IndexedColors.TEAL);
        rest.setCellStyle(restStyle);
        rest.setCellValue("CAMINHO REST\n(COLE OU SELECIONE A PASTA)");

        Cell entries = header.getCell(ENTRY_OUTPUT_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle entriesStyle = workbook.createCellStyle();
        entriesStyle.cloneStyleFrom(defaultHeader);
        setFill(entriesStyle, "0E7490", IndexedColors.TEAL);
        entries.setCellStyle(entriesStyle);
        entries.setCellValue("CAMINHO ENTRADA/SAIDA\n(DUPLO-CLIQUE)");

        Cell certificate = header.getCell(CERTIFICATE_PATH_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle certificateStyle = workbook.createCellStyle();
        certificateStyle.cloneStyleFrom(defaultHeader);
        setFill(certificateStyle, "14B8A6", IndexedColors.TEAL);
        certificate.setCellStyle(certificateStyle);
        certificate.setCellValue("CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)");

        Cell certificateExpiry = header.getCell(CERTIFICATE_EXPIRY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle certificateExpiryStyle = workbook.createCellStyle();
        certificateExpiryStyle.cloneStyleFrom(defaultHeader);
        setFill(certificateExpiryStyle, "16A34A", IndexedColors.GREEN);
        certificateExpiry.setCellStyle(certificateExpiryStyle);
        certificateExpiry.setCellValue("VALIDADE CERTIFICADO DIGITAL");

        Cell certificatePassword = header.getCell(CERTIFICATE_PASSWORD_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle certificatePasswordStyle = workbook.createCellStyle();
        certificatePasswordStyle.cloneStyleFrom(defaultHeader);
        setFill(certificatePasswordStyle, "334155", IndexedColors.BLUE_GREY);
        certificatePassword.setCellStyle(certificatePasswordStyle);
        certificatePassword.setCellValue("SENHA CERTIFICADO DIGITAL\n(OPCIONAL)");

        Cell sourceOnly = header.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle sourceOnlyStyle = workbook.createCellStyle();
        sourceOnlyStyle.cloneStyleFrom(defaultHeader);
        setFill(sourceOnlyStyle, "7C3AED", IndexedColors.VIOLET);
        sourceOnly.setCellStyle(sourceOnlyStyle);
        sourceOnly.setCellValue("SOMENTE ORIGEM");

        setApiHeader(workbook, header, API_PN_ACTIVE_COLUMN, "IMPORT API PN ATIVO", "166534", IndexedColors.GREEN);
        setApiHeader(workbook, header, API_PN_CERTIFICATE_FOLDER_COLUMN, "CERTIFICADO API PN PASTA", "0F766E", IndexedColors.TEAL);
        setApiHeader(workbook, header, API_PN_CERTIFICATE_FILE_COLUMN, "CERTIFICADO API PN ARQUIVO", "0F766E", IndexedColors.TEAL);
        setApiHeader(workbook, header, API_PN_CERTIFICATE_ALIAS_COLUMN, "CERTIFICADO API PN ALIAS", "0E7490", IndexedColors.TEAL);
        setApiHeader(workbook, header, API_PN_CERTIFICATE_EXPIRY_COLUMN, "VALIDADE CERTIFICADO API PN", "15803D", IndexedColors.GREEN);
        setApiHeader(workbook, header, API_PN_CERTIFICATE_ROOT_CNPJ_COLUMN, "CNPJ RAIZ CERTIFICADO", "2563EB", IndexedColors.BLUE);
        setApiHeader(workbook, header, API_PN_MODE_COLUMN, "MODO API PN", "4338CA", IndexedColors.INDIGO);
        setApiHeader(workbook, header, API_PN_ENVIRONMENT_COLUMN, "AMBIENTE API PN", "4338CA", IndexedColors.INDIGO);
        setApiHeader(workbook, header, API_PN_STATUS_COLUMN, "STATUS API PN", "92400E", IndexedColors.BROWN);
        setApiHeader(workbook, header, API_PN_LAST_NSU_COLUMN, "ULTIMO NSU API PN", "334155", IndexedColors.BLUE_GREY);

        header.getCell(CNPJ_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("STATUS CNPJ");
        header.getCell(REST_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("STATUS REST");
        header.getCell(DUPLICATE_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("STATUS DUPLICIDADE");
        header.getCell(PATH_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("STATUS CAMINHO");
        header.getCell(CERTIFICATE_DAYS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("DIAS CERTIFICADO");
        header.getCell(CERTIFICATE_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("STATUS CERTIFICADO");
        header.getCell(CERTIFICATE_RANK_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("RANK CERTIFICADO");
    }

    private static void setApiHeader(Workbook workbook, Row header, int column, String label,
                                     String fill, IndexedColors fallback) {
        Cell cell = header.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(header.getCell(SOURCE_ONLY_COLUMN).getCellStyle());
        setFill(style, fill, fallback);
        cell.setCellStyle(style);
        cell.setCellValue(label);
    }

    private static void styleOperationalRows(Workbook workbook, Sheet sheet, int lastReadyRow) {
        DataFormat dataFormat = workbook.createDataFormat();
        RowStyles even = rowStyles(workbook, dataFormat, "F8FBFF", "E3FFF4", "F9FAFB");
        RowStyles odd = rowStyles(workbook, dataFormat, "EEF6FF", "D7FBEA", "F3F6FA");

        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            row.setZeroHeight(false);
            row.setHeightInPoints(22);
            RowStyles styles = rowIndex % 2 == 0 ? even : odd;
            for (int column = CLIENT_COLUMN; column <= LAST_OPERATIONAL_COLUMN; column++) {
                Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(styleFor(column, cell, styles));
            }
        }
    }

    private static CellStyle styleFor(int column, Cell cell, RowStyles styles) {
        if (column == DMS_COLUMN || column == REST_COLUMN || column == ENTRY_OUTPUT_COLUMN
                || column == CERTIFICATE_PATH_COLUMN || column == API_PN_CERTIFICATE_FOLDER_COLUMN
                || column == API_PN_CERTIFICATE_FILE_COLUMN) {
            return cell.toString().isBlank() ? styles.emptyPath() : styles.filledPath();
        }
        if (column == CERTIFICATE_EXPIRY_COLUMN || column == API_PN_CERTIFICATE_EXPIRY_COLUMN) {
            return styles.date();
        }
        if (column == CLIENT_COLUMN) {
            return styles.client();
        }
        return styles.standard();
    }

    private static RowStyles rowStyles(Workbook workbook, DataFormat dataFormat,
                                       String baseFill, String filledPathFill, String emptyPathFill) {
        CellStyle standard = operationalStyle(workbook, dataFormat, baseFill);
        CellStyle client = workbook.createCellStyle();
        client.cloneStyleFrom(standard);
        Font clientFont = workbook.createFont();
        clientFont.setBold(true);
        clientFont.setFontHeightInPoints((short) 10);
        setFontColor(clientFont, "0B2436", IndexedColors.DARK_BLUE);
        client.setFont(clientFont);

        CellStyle filledPath = operationalStyle(workbook, dataFormat, filledPathFill);
        Font pathFont = workbook.createFont();
        pathFont.setFontHeightInPoints((short) 9);
        setFontColor(pathFont, "064E3B", IndexedColors.GREEN);
        filledPath.setFont(pathFont);

        CellStyle emptyPath = operationalStyle(workbook, dataFormat, emptyPathFill);
        Font emptyPathFont = workbook.createFont();
        emptyPathFont.setFontHeightInPoints((short) 9);
        setFontColor(emptyPathFont, "64748B", IndexedColors.GREY_50_PERCENT);
        emptyPath.setFont(emptyPathFont);

        CellStyle date = workbook.createCellStyle();
        date.cloneStyleFrom(standard);
        date.setDataFormat(dataFormat.getFormat("dd/mm/yyyy"));
        return new RowStyles(standard, client, filledPath, emptyPath, date);
    }

    private static CellStyle operationalStyle(Workbook workbook, DataFormat dataFormat, String fill) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat.getFormat("@"));
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        setFill(style, fill, IndexedColors.WHITE);
        setAllBorders(style, BorderStyle.THIN, "D7E3F0", IndexedColors.GREY_25_PERCENT);
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);
        setFontColor(font, "172033", IndexedColors.BLACK);
        style.setFont(font);
        return style;
    }

    private static void populateHelperFormulas(Workbook workbook, Sheet sheet, int lastReadyRow) {
        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle helperStyle = operationalStyle(workbook, dataFormat, "F8FBFF");
        for (int rowIndex = FIRST_DATA_ROW_INDEX; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int excelRow = rowIndex + 1;
            row.getCell(CNPJ_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula(cnpjStatusFormula(excelRow));
            row.getCell(REST_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(A%d=\"\",\"\",IF(R%d=\"\",\"REST PENDENTE\",\"REST OK\"))"
                            .formatted(excelRow, excelRow));
            row.getCell(DUPLICATE_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(A%d=\"\",\"\",IF(AND(R%d<>\"\",COUNTIFS($D$3:$D$%d,D%d,$R$3:$R$%d,\"<>\")>1),\"DUPLICADO\",\"OK\"))"
                            .formatted(excelRow, excelRow, lastReadyRow + 1, excelRow, lastReadyRow + 1));
            row.getCell(PATH_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(A%d=\"\",\"\",IF(OR(LEN(Q%d)>180,LEN(R%d)>180,LEN(S%d)>180,LEN(T%d)>180),\"CAMINHO LONGO\",\"OK\"))"
                            .formatted(excelRow, excelRow, excelRow, excelRow, excelRow));
            row.getCell(CERTIFICATE_DAYS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(A%d=\"\",\"\",IF(U%d=\"\",\"\",IFERROR(INT(U%d-TODAY()),\"\")))"
                            .formatted(excelRow, excelRow, excelRow));
            row.getCell(CERTIFICATE_STATUS_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(A%d=\"\",\"\",IF(AND(T%d=\"\",U%d=\"\"),\"\",IF(T%d=\"\",\"SEM CAMINHO\",IF(U%d=\"\",\"SEM DATA\",IFERROR(IF(AB%d<15,\"VERMELHO\",IF(AB%d<=30,\"AMARELO\",\"VERDE\")),\"DATA INVALIDA\")))))"
                            .formatted(excelRow, excelRow, excelRow, excelRow, excelRow, excelRow, excelRow));
            row.getCell(CERTIFICATE_RANK_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    .setCellFormula("IF(OR(A%d=\"\",U%d=\"\",NOT(ISNUMBER(AB%d))),\"\",RANK(AB%d,$AB$3:$AB$%d,1)+COUNTIFS($AB$3:AB%d,AB%d)-1)"
                            .formatted(excelRow, excelRow, excelRow, excelRow, lastReadyRow + 1, excelRow, excelRow));
            for (int column = CNPJ_STATUS_COLUMN; column <= LAST_HELPER_COLUMN; column++) {
                row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellStyle(helperStyle);
            }
        }
    }

    private static void clearStaleLegacyHelperColumns(Sheet sheet, int lastReadyRow) {
        if (LEGACY_LAST_HELPER_COLUMN <= LAST_HELPER_COLUMN) {
            return;
        }
        for (int rowIndex = 0; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                clearCells(row, LAST_HELPER_COLUMN + 1, LEGACY_LAST_HELPER_COLUMN);
            }
        }
    }

    private static String cnpjStatusFormula(int excelRow) {
        String digits = "IF(ISNUMBER(D%d),TEXT(D%d,\"00000000000000\"),SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(SUBSTITUTE(D%d,\".\",\"\"),\"/\",\"\"),\"-\",\"\"),\" \",\"\"),CHAR(160),\"\"))"
                .formatted(excelRow, excelRow, excelRow);
        return "IF(A%d=\"\",\"\",IF(LEN(%s)<>14,\"CNPJ INVALIDO\",\"OK\"))"
                .formatted(excelRow, digits);
    }

    private static void markInvalidCnpj(Workbook workbook, Sheet sheet) {
        CellStyle invalidStyle = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        invalidStyle.setDataFormat(dataFormat.getFormat("@"));
        setFill(invalidStyle, "FFF2B8", IndexedColors.LIGHT_YELLOW);
        setAllBorders(invalidStyle, BorderStyle.THIN, "F59E0B", IndexedColors.ORANGE);
        CreationHelper helper = workbook.getCreationHelper();
        var drawing = sheet.createDrawingPatriarch();
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Cell cnpj = row.getCell(CNPJ_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String value = cnpjText(cnpj);
            if (isBlankRow(row) || isValidOrFixableCnpj(value)) {
                continue;
            }
            cnpj.setCellStyle(invalidStyle);
            markSourceOnlyCandidate(row);
            if (cnpj.getCellComment() != null) {
                continue;
            }
            var anchor = helper.createClientAnchor();
            anchor.setCol1(CNPJ_COLUMN);
            anchor.setCol2(CNPJ_COLUMN + 3);
            anchor.setRow1(rowIndex);
            anchor.setRow2(rowIndex + 3);
            var comment = drawing.createCellComment(anchor);
            comment.setString(helper.createRichTextString("Revisar CNPJ antes de importar para o sistema."));
            comment.setAuthor("Renomeador NFS-e");
            cnpj.setCellComment(comment);
        }
    }

    private static void markSourceOnlyCandidate(Row row) {
        Cell rest = row.getCell(REST_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (rest == null || rest.toString().isBlank()) {
            return;
        }
        Cell sourceOnly = row.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        if (sourceOnly.toString().isBlank()) {
            sourceOnly.setCellValue("SIM");
        }
    }

    private static void clearSourceOnlyForValidClients(Sheet sheet, int lastReadyRow) {
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastReadyRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            Cell cnpj = row.getCell(CNPJ_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cnpj == null || !isValidOrFixableCnpj(cnpjText(cnpj))) {
                continue;
            }
            Cell sourceOnly = row.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (sourceOnly != null) {
                sourceOnly.setBlank();
            }
        }
    }

    private static boolean isBlankRow(Row row) {
        Cell client = row.getCell(CLIENT_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Cell cnpj = row.getCell(CNPJ_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return (client == null || client.toString().isBlank()) && (cnpj == null || cnpjText(cnpj).isBlank());
    }

    private static String cnpjText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
                return String.valueOf((long) value);
            }
        }
        return FORMATTER.formatCellValue(cell).strip();
    }

    private static boolean isValidOrFixableCnpj(String value) {
        String digits = NON_DIGITS.matcher(value == null ? "" : value).replaceAll("");
        return isValidCnpj(digits) || digits.length() == 13 && isValidCnpj("0" + digits);
    }

    private static boolean isValidCnpj(String digits) {
        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 12) == Character.digit(digits.charAt(12), 10)
                && checkDigit(digits, 13) == Character.digit(digits.charAt(13), 10);
    }

    private static int checkDigit(String digits, int length) {
        int[] weights = length == 12
                ? new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.digit(digits.charAt(i), 10) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static CellStyle solidStyle(Workbook workbook, String fill, String fontColor,
                                        short fontSize, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        setFill(style, fill, IndexedColors.DARK_BLUE);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setAllBorders(style, BorderStyle.THIN, "123B5D", IndexedColors.BLUE_GREY);
        Font font = workbook.createFont();
        font.setFontHeightInPoints(fontSize);
        font.setBold(bold);
        setFontColor(font, fontColor, IndexedColors.WHITE);
        style.setFont(font);
        return style;
    }

    private static CellStyle dashboardStyle(Workbook workbook, String fill, String fontColor,
                                            short fontSize, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        setFill(style, fill, IndexedColors.DARK_BLUE);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setFontHeightInPoints(fontSize);
        font.setBold(bold);
        setFontColor(font, fontColor, IndexedColors.WHITE);
        style.setFont(font);
        return style;
    }

    private static CellStyle dashboardBackgroundStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        setFill(style, "061A2F", IndexedColors.DARK_BLUE);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        setFontColor(font, "EAF6FF", IndexedColors.WHITE);
        style.setFont(font);
        return style;
    }

    private static CellStyle dashboardCanvasBackgroundStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        setFill(style, "111827", IndexedColors.GREY_80_PERCENT);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        setFontColor(font, "CBD5E1", IndexedColors.GREY_25_PERCENT);
        style.setFont(font);
        return style;
    }

    private static void merge(Sheet sheet, int firstRow, int lastRow, int firstColumn, int lastColumn) {
        CellRangeAddress range = new CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn);
        if (!isMerged(sheet, range)) {
            sheet.addMergedRegion(range);
        }
    }

    private static void setFill(CellStyle style, String rgbHex, IndexedColors fallback) {
        if (style instanceof XSSFCellStyle xssfStyle) {
            xssfStyle.setFillForegroundColor(color(rgbHex));
        } else {
            style.setFillForegroundColor(fallback.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private static void setFontColor(Font font, String rgbHex, IndexedColors fallback) {
        if (font instanceof XSSFFont xssfFont) {
            xssfFont.setColor(color(rgbHex));
        } else {
            font.setColor(fallback.getIndex());
        }
    }

    private static void setAllBorders(CellStyle style, BorderStyle border, String rgbHex, IndexedColors fallback) {
        style.setBorderTop(border);
        style.setBorderRight(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor color = color(rgbHex);
            xssfStyle.setTopBorderColor(color);
            xssfStyle.setRightBorderColor(color);
            xssfStyle.setBottomBorderColor(color);
            xssfStyle.setLeftBorderColor(color);
            return;
        }
        style.setTopBorderColor(fallback.getIndex());
        style.setRightBorderColor(fallback.getIndex());
        style.setBottomBorderColor(fallback.getIndex());
        style.setLeftBorderColor(fallback.getIndex());
    }

    private static void setBorderColors(CellStyle style, String rgbHex, IndexedColors fallback) {
        if (style instanceof XSSFCellStyle xssfStyle) {
            XSSFColor color = color(rgbHex);
            xssfStyle.setTopBorderColor(color);
            xssfStyle.setRightBorderColor(color);
            xssfStyle.setBottomBorderColor(color);
            xssfStyle.setLeftBorderColor(color);
            return;
        }
        style.setTopBorderColor(fallback.getIndex());
        style.setRightBorderColor(fallback.getIndex());
        style.setBottomBorderColor(fallback.getIndex());
        style.setLeftBorderColor(fallback.getIndex());
    }

    private static void setBottomBorder(CellStyle style, BorderStyle border, String rgbHex, IndexedColors fallback) {
        style.setBorderBottom(border);
        if (style instanceof XSSFCellStyle xssfStyle) {
            xssfStyle.setBottomBorderColor(color(rgbHex));
        } else {
            style.setBottomBorderColor(fallback.getIndex());
        }
    }

    private static XSSFColor color(String rgbHex) {
        int rgb = Integer.parseInt(rgbHex, 16);
        return new XSSFColor(new byte[]{
                (byte) ((rgb >> 16) & 0xFF),
                (byte) ((rgb >> 8) & 0xFF),
                (byte) (rgb & 0xFF)
        }, COLOR_MAP);
    }

    private static String columnName(int zeroBasedColumn) {
        StringBuilder name = new StringBuilder();
        int column = zeroBasedColumn + 1;
        while (column > 0) {
            int remainder = (column - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return name.toString();
    }

    private record RowStyles(CellStyle standard, CellStyle client, CellStyle filledPath, CellStyle emptyPath,
                             CellStyle date) {
    }
}
