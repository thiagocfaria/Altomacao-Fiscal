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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        if (Files.exists(outputYaml) && !overwrite) {
            throw new IllegalArgumentException("Arquivo de saida ja existe: " + outputYaml);
        }
        try (InputStream input = Files.newInputStream(workbookPath);
             Workbook workbook = WorkbookFactory.create(input)) {
            List<ImportedCompany> companies = readCompanies(sheet(workbook, sheetName));
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

    private static Sheet sheet(Workbook workbook, String sheetName) {
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
        return workbook.getSheetAt(0);
    }

    private static List<ImportedCompany> readCompanies(Sheet sheet) {
        HeaderRow headerRow = findHeaderRow(sheet);
        Row header = headerRow.row();
        Map<String, Integer> columns = columns(header);
        int nameColumn = requiredColumn(columns, "empresa");
        int taxColumn = requiredColumn(columns, "cnpj");
        int pathColumn = requiredColumn(columns, "caminho");
        Integer sourceOnlyColumn = columns.get("somenteorigem");

        List<ImportedCompany> companies = new ArrayList<>();
        Map<String, Integer> idCounts = new HashMap<>();
        for (int rowIndex = headerRow.index() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String name = text(row.getCell(nameColumn));
            String taxId = normalizeTaxId(text(row.getCell(taxColumn)));
            String path = pathFrom(row.getCell(pathColumn));
            boolean sourceOnly = sourceOnlyColumn != null && isAffirmative(text(row.getCell(sourceOnlyColumn)));
            if (name.isBlank() && taxId.isBlank() && path.isBlank()) {
                continue;
            }
            if (!path.isBlank() && (name.isBlank() || taxId.isBlank())) {
                throw new IllegalArgumentException("Linha incompleta na planilha: " + (rowIndex + 1));
            }
            if (name.isBlank() || taxId.isBlank()) {
                continue;
            }
            if (!isValidOrFixableCnpj(taxId)) {
                if (path.isBlank()) {
                    continue;
                }
                if (!sourceOnly) {
                    throw new IllegalArgumentException("CNPJ invalido na linha " + (rowIndex + 1)
                            + "; corrija o CNPJ ou marque SOMENTE ORIGEM como SIM");
                }
                companies.add(new ImportedCompany(uniqueIdFor(name, idCounts), name, taxId, false,
                        Path.of(path), true));
                continue;
            }
            if (sourceOnly && path.isBlank()) {
                throw new IllegalArgumentException("SOMENTE ORIGEM exige CAMINHO REST preenchido na linha "
                        + (rowIndex + 1));
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
                    .append("    meses: []\n")
                    .append("    pastaBase: \"").append(escape(company.path().toString().replace("\\", "/"))).append("\"\n")
                    .append("    subpastaMes: \"{AAAA}/{MM}\"\n")
                    .append("    pastas:\n")
                    .append("      entrada: \".\"\n")
                    .append("      processados: \"processados\"\n")
                    .append("      revisar: \"revisar\"\n")
                    .append("      originais: \"originais\"\n")
                    .append("      logs: \"logs\"\n")
                    .append("      canceladas: \"revisar/canceladas\"\n")
                    .append("      ledger: \"logs/processados.idx\"\n");
        }
        return yaml.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record HeaderRow(int index, Row row) {
    }

    private record ImportedCompany(String id, String name, String taxId, boolean pathMissing, Path path,
                                   boolean sourceOnly) {
    }
}
