package br.com.nfse.importadorpn.configuracao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public final class LeitorPlanilhaFiscal {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("pt-BR"));
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_HEADER_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final DateTimeFormatter DATE_BR = new DateTimeFormatterBuilder()
            .appendPattern("dd/MM/uuuu")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    public CadastroImportacao ler(Path workbookPath) throws IOException {
        return ler(workbookPath, Optional.empty());
    }

    public CadastroImportacao ler(Path workbookPath, YearMonth month) throws IOException {
        return ler(workbookPath, Optional.of(month), false);
    }

    public CadastroImportacao lerTodasAbas(Path workbookPath) throws IOException {
        return ler(workbookPath, Optional.empty(), true);
    }

    private CadastroImportacao ler(Path workbookPath, Optional<YearMonth> month) throws IOException {
        return ler(workbookPath, month, false);
    }

    private CadastroImportacao ler(Path workbookPath, Optional<YearMonth> month, boolean todasAbas) throws IOException {
        try (InputStream input = java.nio.file.Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(input)) {
            List<EmpresaImportacao> empresas = new ArrayList<>();
            Optional<Path> entradaRest = Optional.empty();
            for (Sheet sheet : abasParaLeitura(workbook, month, todasAbas)) {
                if (!deveLerAba(sheet.getSheetName())) {
                    continue;
                }
                Optional<HeaderRow> header = findHeaderRow(sheet);
                if (header.isEmpty()) {
                    continue;
                }
                LeituraAba leitura = lerAba(sheet, header.get(), todasAbas);
                empresas.addAll(leitura.empresas());
                if (entradaRest.isEmpty()) {
                    entradaRest = leitura.entradaRest();
                }
            }
            return new CadastroImportacao(empresas, entradaRest);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Falha ao ler PLANILHA_FISCAL: " + exception.getMessage(), exception);
        }
    }

    private static List<Sheet> abasParaLeitura(Workbook workbook, Optional<YearMonth> month, boolean todasAbas) {
        if (todasAbas) {
            List<Sheet> sheets = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(workbook.getSheetAt(i));
            }
            return sheets;
        }
        YearMonth mesReferencia = month.orElseGet(YearMonth::now);
        Sheet mesSelecionado = workbook.getSheet(monthlySheetName(mesReferencia));
        if (mesSelecionado != null) {
            return List.of(mesSelecionado);
        }
        List<Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            sheets.add(workbook.getSheetAt(i));
        }
        return sheets;
    }

    private static String monthlySheetName(YearMonth month) {
        return "CADASTRO " + switch (month.getMonth()) {
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

    private static boolean deveLerAba(String name) {
        String normalized = normalizeHeader(name);
        return normalized.startsWith("cadastro") || normalized.equals("dashboardfiscal");
    }

    private static LeituraAba lerAba(Sheet sheet, HeaderRow headerRow, boolean incluirRotasInativas) {
        Map<String, Integer> columns = columns(headerRow.row());
        int empresaColumn = requiredColumn(columns, "empresa");
        int cnpjColumn = requiredColumn(columns, "cnpj");
        Integer caminhoRestColumn = columns.get("caminhorest");
        Integer caminhoDmsColumn = columns.get("caminhodms");
        Integer somenteOrigemColumn = columns.get("somenteorigem");
        Integer ativoColumn = columns.get("importapipnativo");
        Integer certPastaApiColumn = columns.get("certificadoapipnpasta");
        Integer certPastaLegadoColumn = columns.get("caminhocertificadodigital");
        Integer certArquivoApiColumn = columns.get("certificadoapipnarquivo");
        Integer certArquivoNomeColumn = columns.get("nomecertificadodigital");
        Integer certAliasColumn = columns.get("certificadoapipnalias");
        Integer senhaCertificadoApiColumn = columns.get("senhacertificadoapipn");
        Integer senhaCertificadoLegadoColumn = columns.get("senhacertificadodigital");
        Integer validadeApiColumn = columns.get("validadecertificadoapipn");
        Integer validadeLegadoColumn = columns.get("validadecertificadodigital");
        if (caminhoRestColumn == null) {
            caminhoRestColumn = columns.get("caminho");
        }

        List<EmpresaImportacao> empresas = new ArrayList<>();
        Optional<Path> entradaRest = Optional.empty();
        for (int rowIndex = headerRow.index() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String nome = text(row.getCell(empresaColumn));
            String cnpj = normalizeCnpj(text(row.getCell(cnpjColumn)));
            Optional<Path> caminhoRest = optionalPath(pathFrom(cell(row, caminhoRestColumn)));
            Optional<Path> caminhoDms = optionalPath(pathFrom(cell(row, caminhoDmsColumn)));
            boolean somenteOrigem = somenteOrigemColumn != null && affirmative(text(row.getCell(somenteOrigemColumn)));
            boolean ativo = ativoColumn == null || affirmative(text(row.getCell(ativoColumn)));
            if (nome.isBlank() && cnpj.isBlank() && caminhoRest.isEmpty() && caminhoDms.isEmpty()) {
                continue;
            }
            if (somenteOrigem && nomeNormalizado(nome).equals("importapipnentradarest")) {
                if (entradaRest.isEmpty()) {
                    entradaRest = caminhoRest;
                }
                continue;
            }
            if (cnpj.isBlank()) {
                continue;
            }
            if (!ativo) {
                continue;
            }
            empresas.add(new EmpresaImportacao(nome, cnpj, caminhoRest, caminhoDms,
                    optionalPath(textoPrimeiro(row, certPastaApiColumn, certPastaLegadoColumn)),
                    optionalText(textoPrimeiro(row, certArquivoNomeColumn, certArquivoApiColumn)),
                    optionalText(text(cell(row, certAliasColumn))).or(() -> optionalText(cnpj)),
                    optionalText(textoPrimeiro(row, senhaCertificadoApiColumn, senhaCertificadoLegadoColumn)),
                    parseDate(cell(row, validadeApiColumn)).or(() -> parseDate(cell(row, validadeLegadoColumn))),
                    sheet.getSheetName(), rowIndex + 1));
        }
        return new LeituraAba(empresas, entradaRest);
    }

    private static String textoPrimeiro(Row row, Integer... columns) {
        for (Integer column : columns) {
            String value = text(cell(row, column));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Optional<HeaderRow> findHeaderRow(Sheet sheet) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> columns = columns(row);
            if (columns.containsKey("empresa") && columns.containsKey("cnpj")
                    && (columns.containsKey("caminhorest") || columns.containsKey("caminho"))) {
                return Optional.of(new HeaderRow(rowIndex, row));
            }
        }
        return Optional.empty();
    }

    private static Map<String, Integer> columns(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            columns.putIfAbsent(normalizeColumn(text(cell)), cell.getColumnIndex());
        }
        return columns;
    }

    private static String normalizeColumn(String value) {
        String normalized = normalizeHeader(value);
        if (normalized.equals("cliente") || normalized.equals("nome") || normalized.equals("razaosocial")) {
            return "empresa";
        }
        if (normalized.equals("cnpjtomador")) {
            return "cnpj";
        }
        if (normalized.equals("pasta") || normalized.equals("diretorio")) {
            return "caminho";
        }
        if (normalized.startsWith("caminhorest")) {
            return "caminhorest";
        }
        if (normalized.startsWith("caminhodms")) {
            return "caminhodms";
        }
        if (normalized.startsWith("somenteorigem")) {
            return "somenteorigem";
        }
        if (normalized.startsWith("certificadoapipnpasta")) {
            return "certificadoapipnpasta";
        }
        if (normalized.startsWith("caminhocertificadodigital")) {
            return "caminhocertificadodigital";
        }
        if (normalized.startsWith("certificadoapipnarquivo")) {
            return "certificadoapipnarquivo";
        }
        if (normalized.startsWith("nomecertificadodigital")) {
            return "nomecertificadodigital";
        }
        if (normalized.startsWith("certificadoapipnalias")) {
            return "certificadoapipnalias";
        }
        if (normalized.startsWith("senhacertificadoapipn")) {
            return "senhacertificadoapipn";
        }
        if (normalized.startsWith("senhacertificadodigital")) {
            return "senhacertificadodigital";
        }
        if (normalized.startsWith("validadecertificadoapipn")) {
            return "validadecertificadoapipn";
        }
        if (normalized.startsWith("validadecertificadodigital")) {
            return "validadecertificadodigital";
        }
        return normalized;
    }

    private static String normalizeHeader(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .transform(DIACRITICS::matcher).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .transform(NON_HEADER_CHARS::matcher).replaceAll("");
    }

    private static String nomeNormalizado(String value) {
        return normalizeHeader(value);
    }

    private static int requiredColumn(Map<String, Integer> columns, String key) {
        Integer column = columns.get(key);
        if (column == null) {
            throw new IllegalArgumentException("cabecalho obrigatorio ausente: " + key);
        }
        return column;
    }

    private static Cell cell(Row row, Integer column) {
        return column == null ? null : row.getCell(column);
    }

    private static String pathFrom(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getHyperlink() != null && cell.getHyperlink().getAddress() != null
                && !cell.getHyperlink().getAddress().isBlank()) {
            return cell.getHyperlink().getAddress();
        }
        return text(cell);
    }

    private static Optional<Path> optionalPath(String value) {
        return optionalText(value).map(Path::of);
    }

    private static Optional<String> optionalText(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private static Optional<LocalDate> parseDate(Cell cell) {
        if (cell == null) {
            return Optional.empty();
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return Optional.of(cell.getLocalDateTimeCellValue().toLocalDate());
        }
        String value = text(cell);
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, DATE_BR));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean affirmative(String value) {
        String normalized = normalizeHeader(value);
        return normalized.equals("sim") || normalized.equals("s") || normalized.equals("true")
                || normalized.equals("x") || normalized.equals("1");
    }

    private static String normalizeCnpj(String value) {
        return NON_DIGITS.matcher(value == null ? "" : value).replaceAll("");
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
                return String.valueOf((long) value);
            }
        }
        return FORMATTER.formatCellValue(cell).strip();
    }

    private record HeaderRow(int index, Row row) {
    }

    private record LeituraAba(List<EmpresaImportacao> empresas, Optional<Path> entradaRest) {
    }
}
