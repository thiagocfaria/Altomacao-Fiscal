package br.com.nfse.renomeador.config.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ExcelCompanyImporter {
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("pt-BR"));
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_HEADER_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NON_ID_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    public int importToYaml(Path workbookPath, Path outputYaml, String sheetName) throws IOException {
        return importToYaml(workbookPath, outputYaml, sheetName, false);
    }

    public int importToYaml(Path workbookPath, Path outputYaml, String sheetName, boolean overwrite) throws IOException {
        return importToYaml(workbookPath, outputYaml, sheetName, overwrite, Optional.empty(), LocalDate.now());
    }

    public int importToYaml(Path workbookPath, Path outputYaml, String sheetName, boolean overwrite,
                            Optional<YearMonth> month, LocalDate executionDate) throws IOException {
        if (Files.exists(outputYaml) && !overwrite) {
            throw new IllegalArgumentException("Arquivo de saida ja existe: " + outputYaml);
        }
        try (InputStream input = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(input)) {
            List<ImportedCompany> companies = readCompanies(sheet(workbook, sheetName, month, executionDate));
            if (companies.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma empresa valida encontrada na planilha");
            }
            if (outputYaml.getParent() != null) {
                Files.createDirectories(outputYaml.getParent());
            }
            Files.writeString(outputYaml, yamlFor(companies), StandardCharsets.UTF_8);
            return companies.size();
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Falha ao importar planilha: " + exception.getMessage(), exception);
        }
    }

    public int importAllMonthsToYaml(Path workbookPath, Path outputYaml, boolean overwrite) throws IOException {
        return importAllMonthsToYaml(workbookPath, outputYaml, overwrite, LocalDate.now());
    }

    public int importAllMonthsToYaml(Path workbookPath, Path outputYaml, boolean overwrite,
                                     LocalDate executionDate) throws IOException {
        if (Files.exists(outputYaml) && !overwrite) {
            throw new IllegalArgumentException("Arquivo de saida ja existe: " + outputYaml);
        }
        try (InputStream input = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(input)) {
            int sheetsFound = 0;
            List<ImportedCompany> allCompanies = new ArrayList<>();
            Map<String, Integer> idCounts = new HashMap<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Optional<YearMonth> month = monthFromSheetName(sheet.getSheetName(), executionDate.getYear());
                if (month.isEmpty()) continue;
                sheetsFound++;
                try {
                    allCompanies.addAll(readCompaniesForMonth(sheet, month.get(), idCounts));
                } catch (IllegalArgumentException e) {
                    if (e.getMessage() != null && e.getMessage().contains("cabecalho obrigatorio")) {
                        continue;
                    }
                    throw e;
                }
            }
            if (sheetsFound == 0) {
                // Sem abas CADASTRO: usar fallback de aba unica (compatibilidade com planilhas legadas e testes)
                List<ImportedCompany> companies = readCompanies(sheet(workbook, null, Optional.empty(), executionDate));
                if (companies.isEmpty()) {
                    throw new IllegalArgumentException("Nenhuma empresa valida encontrada na planilha");
                }
                if (outputYaml.getParent() != null) Files.createDirectories(outputYaml.getParent());
                Files.writeString(outputYaml, yamlFor(companies), StandardCharsets.UTF_8);
                return companies.size();
            }
            if (allCompanies.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma empresa valida encontrada em nenhuma aba CADASTRO");
            }
            if (outputYaml.getParent() != null) {
                Files.createDirectories(outputYaml.getParent());
            }
            Files.writeString(outputYaml, yamlFor(allCompanies), StandardCharsets.UTF_8);
            return allCompanies.size();
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Falha ao importar planilha: " + exception.getMessage(), exception);
        }
    }

    private static Optional<YearMonth> monthFromSheetName(String name, int year) {
        if (name == null) return Optional.empty();
        String upper = name.strip().toUpperCase(Locale.ROOT);
        if (!upper.startsWith("CADASTRO ")) return Optional.empty();
        String monthPart = upper.substring("CADASTRO ".length()).strip();
        Month month = switch (monthPart) {
            case "JANEIRO" -> Month.JANUARY;
            case "FEVEREIRO" -> Month.FEBRUARY;
            case "MARCO" -> Month.MARCH;
            case "ABRIL" -> Month.APRIL;
            case "MAIO" -> Month.MAY;
            case "JUNHO" -> Month.JUNE;
            case "JULHO" -> Month.JULY;
            case "AGOSTO" -> Month.AUGUST;
            case "SETEMBRO" -> Month.SEPTEMBER;
            case "OUTUBRO" -> Month.OCTOBER;
            case "NOVEMBRO" -> Month.NOVEMBER;
            case "DEZEMBRO" -> Month.DECEMBER;
            default -> null;
        };
        return month == null ? Optional.empty() : Optional.of(YearMonth.of(year, month));
    }

    private static List<ImportedCompany> readCompaniesForMonth(Sheet sheet, YearMonth month,
                                                               Map<String, Integer> idCounts) {
        return readCompanies(sheet, idCounts).stream()
                .map(c -> new ImportedCompany(c.id(), c.name(), c.taxId(), c.pathMissing(), c.path(),
                        c.sourceOnly(), Optional.of(month)))
                .toList();
    }

    private static Sheet sheet(Workbook workbook, String sheetName) {
        return sheet(workbook, sheetName, Optional.empty(), LocalDate.now());
    }

    private static Sheet sheet(Workbook workbook, String sheetName, Optional<YearMonth> month,
                               LocalDate executionDate) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet selected = workbook.getSheet(sheetName);
            if (selected == null) {
                throw new IllegalArgumentException("Aba nao encontrada: " + sheetName);
            }
            return selected;
        }
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("Planilha sem abas");
        }
        Sheet monthly = workbook.getSheet(monthlySheetName(month.orElse(YearMonth.from(executionDate))));
        if (monthly != null) {
            return monthly;
        }
        Sheet cadastro = workbook.getSheet("CADASTRO");
        if (cadastro != null) {
            return cadastro;
        }
        Sheet cadastroApril = workbook.getSheet("CADASTRO ABRIL");
        if (cadastroApril != null) {
            return cadastroApril;
        }
        Sheet dashboardFiscal = workbook.getSheet("Dashboard Fiscal");
        if (dashboardFiscal != null) {
            return dashboardFiscal;
        }
        return workbook.getSheetAt(0);
    }

    private static String monthlySheetName(YearMonth month) {
        return "CADASTRO " + monthName(month.getMonth());
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

    private static List<ImportedCompany> readCompanies(Sheet sheet) {
        return readCompanies(sheet, new HashMap<>());
    }

    private static List<ImportedCompany> readCompanies(Sheet sheet, Map<String, Integer> idCounts) {
        HeaderRow headerRow = findHeaderRow(sheet);
        Row header = headerRow.row();
        Map<String, Integer> columns = columns(header);
        int nameColumn = requiredColumn(columns, "empresa");
        int taxColumn = requiredColumn(columns, "cnpj");
        int pathColumn = requiredColumn(columns, "caminho");
        Integer sourceOnlyColumn = columns.get("somenteorigem");

        List<ImportedCompany> companies = new ArrayList<>();
        for (int rowIndex = headerRow.index() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String name = text(row.getCell(nameColumn));
            String taxId = normalizeTaxId(text(row.getCell(taxColumn)));
            String path = pathFrom(row.getCell(pathColumn));
            boolean explicitSourceOnly = sourceOnlyColumn != null && isAffirmative(text(row.getCell(sourceOnlyColumn)));
            boolean validOrFixableCnpj = isValidOrFixableCnpj(taxId);
            boolean sourceOnly = explicitSourceOnly && !validOrFixableCnpj;
            if (name.isBlank() && taxId.isBlank() && path.isBlank()) {
                continue;
            }
            if (explicitSourceOnly && path.isBlank()) {
                continue;
            }
            if (!path.isBlank() && name.isBlank()) {
                throw new IllegalArgumentException("Linha incompleta na planilha: " + (rowIndex + 1));
            }
            if (!path.isBlank() && taxId.isBlank() && !sourceOnly) {
                throw new IllegalArgumentException("Linha incompleta na planilha: " + (rowIndex + 1));
            }
            if (name.isBlank()) {
                continue;
            }
            if (taxId.isBlank()) {
                if (sourceOnly && !path.isBlank()) {
                    companies.add(new ImportedCompany(uniqueIdFor(name, idCounts), name, taxId, false,
                            Path.of(path), true));
                }
                continue;
            }
            if (!validOrFixableCnpj) {
                if (path.isBlank()) {
                    continue;
                }
                if (!explicitSourceOnly) {
                    throw new IllegalArgumentException("CNPJ invalido na linha " + (rowIndex + 1)
                            + "; corrija o CNPJ ou marque SOMENTE ORIGEM como SIM");
                }
                companies.add(new ImportedCompany(uniqueIdFor(name, idCounts), name, taxId, false,
                        Path.of(path), true));
                continue;
            }
            if (sourceOnly && path.isBlank()) {
                continue;
            }
            companies.add(new ImportedCompany(uniqueIdFor(name, idCounts), name, taxId, path.isBlank(),
                    path.isBlank() ? Path.of(".") : Path.of(path), sourceOnly));
        }
        return List.copyOf(companies);
    }

    private static HeaderRow findHeaderRow(Sheet sheet) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row candidate = sheet.getRow(rowIndex);
            if (candidate == null) {
                continue;
            }
            Map<String, Integer> columns = columns(candidate);
            if (columns.containsKey("empresa") && columns.containsKey("cnpj") && columns.containsKey("caminho")) {
                return new HeaderRow(rowIndex, candidate);
            }
        }
        throw new IllegalArgumentException("cabecalho obrigatorio: Empresa, CNPJ, Caminho");
    }

    private static Map<String, Integer> columns(Row header) {
        if (header == null) {
            throw new IllegalArgumentException("cabecalho obrigatorio: Empresa, CNPJ, Caminho");
        }
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            columns.putIfAbsent(normalizeHeader(text(cell)), cell.getColumnIndex());
        }
        return columns;
    }

    private static int requiredColumn(Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null) {
            throw new IllegalArgumentException("cabecalho obrigatorio ausente: " + key);
        }
        return index;
    }

    private static String pathFrom(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getHyperlink() != null && cell.getHyperlink().getAddress() != null
                && !cell.getHyperlink().getAddress().isBlank()) {
            return pathAddress(cell.getHyperlink().getAddress());
        }
        return text(cell);
    }

    private static String pathAddress(String address) {
        try {
            URI uri = URI.create(address);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return Path.of(uri).toString();
            }
        } catch (IllegalArgumentException ignored) {
            // Endereco de hyperlink do Excel tambem pode ser caminho Windows cru.
        }
        return address;
    }

    private static String text(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val) && !Double.isInfinite(val) && Math.abs(val) < 1e15) {
                return String.valueOf((long) val);
            }
        }
        return FORMATTER.formatCellValue(cell).strip();
    }

    private static String normalizeHeader(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .transform(DIACRITICS::matcher).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .transform(NON_HEADER_CHARS::matcher).replaceAll("");
        if (normalized.equals("pasta") || normalized.equals("diretorio")
                || normalized.equals("caminho") || normalized.startsWith("caminhorest")) {
            return "caminho";
        }
        if (normalized.equals("razaosocial") || normalized.equals("nome")
                || normalized.equals("empresa") || normalized.equals("cliente")) {
            return "empresa";
        }
        if (normalized.equals("cnpjtomador")) {
            return "cnpj";
        }
        if (normalized.equals("somenteorigem") || normalized.equals("origem")
                || normalized.equals("pastaorigem") || normalized.equals("sourceonly")) {
            return "somenteorigem";
        }
        return normalized;
    }

    private static boolean isAffirmative(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .transform(DIACRITICS::matcher).replaceAll("")
                .strip()
                .toLowerCase(Locale.ROOT);
        return normalized.equals("sim")
                || normalized.equals("s")
                || normalized.equals("true")
                || normalized.equals("x")
                || normalized.equals("1");
    }

    private static String normalizeTaxId(String value) {
        String digits = NON_DIGITS.matcher(value == null ? "" : value).replaceAll("");
        if (digits.length() == 13 && isValidCnpj("0" + digits)) {
            digits = "0" + digits;
        }
        return digits.length() == 14 ? "%s.%s.%s/%s-%s".formatted(
                digits.substring(0, 2),
                digits.substring(2, 5),
                digits.substring(5, 8),
                digits.substring(8, 12),
                digits.substring(12)
        ) : value;
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

    private static String idFor(String name) {
        String id = Normalizer.normalize(name, Normalizer.Form.NFD)
                .transform(DIACRITICS::matcher).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .transform(NON_ID_CHARS::matcher).replaceAll("_")
                .transform(EDGE_UNDERSCORES::matcher).replaceAll("");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Nao foi possivel gerar id para empresa: " + name);
        }
        return id;
    }

    private static String uniqueIdFor(String name, Map<String, Integer> idCounts) {
        String base = idFor(name);
        int count = idCounts.merge(base, 1, Integer::sum);
        return count == 1 ? base : base + "_" + count;
    }

    private static String yamlFor(List<ImportedCompany> companies) {
        StringBuilder yaml = new StringBuilder("empresas:\n");
        for (ImportedCompany company : companies) {
            yaml.append("  - id: \"").append(escape(company.id())).append("\"\n")
                    .append("    habilitada: ").append(!company.pathMissing()).append("\n")
                    .append("    somenteOrigem: ").append(company.sourceOnly()).append("\n")
                    .append("    cnpjTomador: \"").append(escape(company.taxId())).append("\"\n")
                    .append("    estrategiaMes: \"direto\"\n")
                    .append("    meses: []\n");
            company.month().ifPresent(m -> yaml.append("    mes: \"").append(m).append("\"\n"));
            yaml.append("    pastaBase: \"").append(escape(company.path().toString().replace("\\", "/"))).append("\"\n")
                    .append("    subpastaMes: \"{AAAA}/{MM}\"\n")
                    .append("    pastas:\n")
                    .append("      entrada: \".\"\n")
                    .append("      processados: \"processados\"\n")
                    .append("      canceladas: \"canceladas\"\n");
        }
        return yaml.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record HeaderRow(int index, Row row) {
    }

    private record ImportedCompany(String id, String name, String taxId, boolean pathMissing, Path path,
                                   boolean sourceOnly, Optional<YearMonth> month) {
        ImportedCompany(String id, String name, String taxId, boolean pathMissing, Path path, boolean sourceOnly) {
            this(id, name, taxId, pathMissing, path, sourceOnly, Optional.empty());
        }
    }
}
