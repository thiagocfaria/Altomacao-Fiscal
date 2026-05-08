package br.com.nfse.renomeador.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CompanyRegistryLoader {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> YAML_MAP = new TypeReference<>() {
    };
    private static final Set<String> ROOT_KEYS = Set.of("backendRoot", "empresas");
    private static final Set<String> COMPANY_KEYS = Set.of(
            "id", "habilitada", "cnpjTomador", "estrategiaMes", "meses", "pastaBase",
            "subpastaMes", "pastas", "somenteOrigem", "mes"
    );
    private static final Set<String> FOLDER_KEYS = Set.of(
            "entrada", "processados", "revisar", "originais", "logs", "canceladas", "ledger"
    );

    public CompanyRegistry load(Path yamlPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(yamlPath)) {
            Map<String, Object> root = YAML_MAPPER.readValue(reader, YAML_MAP);
            if (root == null) {
                throw new IllegalArgumentException("Arquivo de configuracao invalido");
            }
            rejectUnknownKeys(root, ROOT_KEYS, "raiz");
            Object companiesNode = root.get("empresas");
            if (!(companiesNode instanceof List<?> companiesList)) {
                throw new IllegalArgumentException("empresas e obrigatorio");
            }
            List<CompanyConfig> companies = new ArrayList<>();
            for (Object item : companiesList) {
                if (!(item instanceof Map<?, ?> companyMap)) {
                    throw new IllegalArgumentException("Empresa invalida em empresas");
                }
                companies.add(toCompany(companyMap));
            }
            String backendRoot = optionalString(root, "backendRoot", "");
            return new CompanyRegistry(companies, backendRoot.isBlank()
                    ? Optional.empty()
                    : Optional.of(resolveBackendRoot(yamlPath, Path.of(backendRoot))));
        }
    }

    private static Path resolveBackendRoot(Path yamlPath, Path backendRoot) {
        if (backendRoot.isAbsolute()) {
            return backendRoot.normalize();
        }
        Path parent = yamlPath.toAbsolutePath().normalize().getParent();
        Path base = parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
        return base.resolve(backendRoot).normalize();
    }

    private static CompanyConfig toCompany(Map<?, ?> map) {
        rejectUnknownKeys(map, COMPANY_KEYS, "empresa");
        String id = requiredString(map, "id");
        boolean sourceOnly = booleanValue(map.get("somenteOrigem"));
        String customerTaxId = sourceOnly
                ? optionalString(map, "cnpjTomador", "")
                : requiredString(map, "cnpjTomador");
        MonthStrategy strategy = MonthStrategy.fromConfig(requiredString(map, "estrategiaMes"));
        Path basePath = Path.of(requiredString(map, "pastaBase"));
        String monthSubfolder = optionalString(map, "subpastaMes", "{AAAA}/{MM}");
        boolean enabled = !map.containsKey("habilitada") || booleanValue(map.get("habilitada"));
        List<String> months = stringList(map.get("meses"));
        CompanyFolders folders = toFolders(map.get("pastas"));

        String mesValue = optionalString(map, "mes", "");
        Optional<YearMonth> importedMonth = mesValue.isBlank() ? Optional.empty()
                : Optional.of(YearMonth.parse(mesValue));
        return new CompanyConfig(id, enabled, customerTaxId, strategy, months, basePath, monthSubfolder,
                folders, sourceOnly, importedMonth);
    }

    private static CompanyFolders toFolders(Object foldersNode) {
        if (!(foldersNode instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("pastas e obrigatorio");
        }
        rejectUnknownKeys(map, FOLDER_KEYS, "pastas");
        String cancelled = optionalString(map, "canceladas", "canceladas");
        if ("revisar/canceladas".equals(cancelled)) {
            cancelled = "canceladas";
        }
        return new CompanyFolders(
                requiredString(map, "entrada"),
                optionalString(map, "processados", "processados"),
                optionalString(map, "revisar", "revisar"),
                optionalString(map, "originais", "originais"),
                optionalString(map, "logs", "logs"),
                cancelled,
                optionalString(map, "ledger", "logs/processados.idx")
        );
    }

    private static String requiredString(Map<?, ?> map, String key) {
        String value = stringValue(map.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " e obrigatorio");
        }
        return value;
    }

    private static String optionalString(Map<?, ?> map, String key, String defaultValue) {
        String value = stringValue(map.get(key));
        return value.isBlank() ? defaultValue : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("meses deve ser uma lista");
        }
        return list.stream().map(CompanyRegistryLoader::stringValue).toList();
    }

    private static void rejectUnknownKeys(Map<?, ?> map, Set<String> allowed, String context) {
        for (Object key : map.keySet()) {
            String name = stringValue(key);
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Campo desconhecido em " + context + ": " + name);
            }
        }
    }
}
