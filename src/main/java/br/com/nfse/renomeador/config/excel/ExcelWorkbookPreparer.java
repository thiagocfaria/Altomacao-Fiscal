package br.com.nfse.renomeador.config.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ExcelWorkbookPreparer {
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final int HEADER_ROW_INDEX = 1;
    private static final int CLIENT_COLUMN = 0;
    private static final int CNPJ_COLUMN = 3;
    private static final int REST_COLUMN = 17;
    private static final int SOURCE_ONLY_COLUMN = 18;
    private static final int LAST_OPERATIONAL_COLUMN = 18;
    private static final int EXTRA_READY_ROWS = 30;
    private static final DefaultIndexedColorMap COLOR_MAP = new DefaultIndexedColorMap();

    public void prepare(Path inputWorkbook, Path outputWorkbook) throws IOException {
        if (outputWorkbook.getParent() != null) {
            Files.createDirectories(outputWorkbook.getParent());
        }
        try (InputStream input = Files.newInputStream(inputWorkbook);
             Workbook workbook = WorkbookFactory.create(input);
             OutputStream output = Files.newOutputStream(outputWorkbook)) {
            Sheet sheet = workbook.getSheet("Dashboard Fiscal");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            prepareSheet(workbook, sheet);
            workbook.write(output);
        }
    }

    private static void prepareSheet(Workbook workbook, Sheet sheet) {
        int lastDataRow = lastDataRow(sheet);
        int lastReadyRow = Math.max(HEADER_ROW_INDEX + EXTRA_READY_ROWS + 1, lastDataRow + EXTRA_READY_ROWS);
        removeBlankRowsAfter(sheet, lastReadyRow);
        ensureOperationalRows(sheet, lastReadyRow);
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setZoom(90);
        sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW_INDEX, lastReadyRow,
                CLIENT_COLUMN, LAST_OPERATIONAL_COLUMN));
        sheet.setColumnWidth(CLIENT_COLUMN, 52 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 26 * 256);
        sheet.setColumnWidth(CNPJ_COLUMN, 20 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 20 * 256);
        sheet.setColumnWidth(REST_COLUMN, 62 * 256);
        sheet.setColumnWidth(16, 44 * 256);
        sheet.setColumnWidth(18, 44 * 256);

        styleTitle(workbook, sheet);
        Row header = sheet.getRow(HEADER_ROW_INDEX);
        if (header != null) {
            styleHeader(workbook, header);
        }
        styleOperationalRows(workbook, sheet, lastReadyRow);
        markInvalidCnpj(workbook, sheet);
    }

    private static void ensureOperationalRows(Sheet sheet, int lastReadyRow) {
        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= lastReadyRow; rowIndex++) {
            if (sheet.getRow(rowIndex) == null) {
                sheet.createRow(rowIndex);
            }
        }
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

    private static void styleTitle(Workbook workbook, Sheet sheet) {
        Row title = sheet.getRow(0);
        if (title == null) {
            title = sheet.createRow(0);
        }
        title.setHeightInPoints(42);
        Cell cell = title.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue("PROTONS");

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

        for (Cell cell : header) {
            cell.setCellStyle(defaultHeader);
        }
        header.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue("CIDADE");

        Cell rest = header.getCell(REST_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle restStyle = workbook.createCellStyle();
        restStyle.cloneStyleFrom(defaultHeader);
        setFill(restStyle, "00B4D8", IndexedColors.TEAL);
        rest.setCellStyle(restStyle);
        rest.setCellValue("CAMINHO REST\n(COLE OU SELECIONE A PASTA)");

        Cell sourceOnly = header.getCell(SOURCE_ONLY_COLUMN, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        CellStyle sourceOnlyStyle = workbook.createCellStyle();
        sourceOnlyStyle.cloneStyleFrom(defaultHeader);
        setFill(sourceOnlyStyle, "7C3AED", IndexedColors.VIOLET);
        sourceOnly.setCellStyle(sourceOnlyStyle);
        sourceOnly.setCellValue("SOMENTE ORIGEM");
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
            row.setHeightInPoints(22);
            RowStyles styles = rowIndex % 2 == 0 ? even : odd;
            for (int column = CLIENT_COLUMN; column <= LAST_OPERATIONAL_COLUMN; column++) {
                Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellStyle(styleFor(column, cell, styles));
            }
        }
    }

    private static CellStyle styleFor(int column, Cell cell, RowStyles styles) {
        if (column == REST_COLUMN) {
            return cell.toString().isBlank() ? styles.emptyPath() : styles.filledPath();
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
        return new RowStyles(standard, client, filledPath, emptyPath);
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
            String value = cnpj.toString();
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

    private static boolean isBlankRow(Row row) {
        Cell client = row.getCell(CLIENT_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        Cell cnpj = row.getCell(CNPJ_COLUMN, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return (client == null || client.toString().isBlank()) && (cnpj == null || cnpj.toString().isBlank());
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

    private record RowStyles(CellStyle standard, CellStyle client, CellStyle filledPath, CellStyle emptyPath) {
    }
}
