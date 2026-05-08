package br.com.nfse.renomeador.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class CompanyRegistryValidator {
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private final MonthlyPathResolver resolver = new MonthlyPathResolver();

    public void validate(CompanyRegistry registry) {
        validateBasics(registry);
        validateResolvedPaths(resolveWithoutExternalParameters(registry));
        validateBackendRoot(registry);
    }

    public void validateBasics(CompanyRegistry registry) {
        Set<String> ids = new HashSet<>();
        Map<String, String> destinationTaxIds = new HashMap<>();
        for (CompanyConfig company : registry.companies()) {
            if (!ids.add(company.id())) {
                throw new IllegalArgumentException("id duplicado: " + company.id());
            }
            if (!company.sourceOnly() && !isValidCnpj(company.customerTaxId())) {
                throw new IllegalArgumentException("CNPJ invalido para empresa " + company.id());
            }
            if (company.enabled() && !company.sourceOnly()) {
                String taxIdKey = destinationTaxIdKey(company);
                String previous = destinationTaxIds.putIfAbsent(taxIdKey, company.id());
                if (previous != null) {
                    throw new IllegalArgumentException("CNPJ duplicado entre empresas "
                            + previous + " e " + company.id() + ": " + company.customerTaxId());
                }
            }
        }
    }

    public void validateResolvedPaths(List<ResolvedCompanyPath> paths) {
        Map<Path, String> inputFolders = new HashMap<>();
        for (ResolvedCompanyPath companyPath : paths) {
            validateResolvedPath(companyPath, inputFolders);
        }
    }

    private List<ResolvedCompanyPath> resolveWithoutExternalParameters(CompanyRegistry registry) {
        return registry.companies().stream()
                .filter(CompanyConfig::enabled)
                .filter(company -> company.monthStrategy() != MonthStrategy.INFORMED)
                .flatMap(company -> resolver.resolve(company, Optional.empty(), LocalDate.now()).stream())
                .toList();
    }

    private static void validateResolvedPath(ResolvedCompanyPath companyPath, Map<Path, String> inputFolders) {
        validateSafeRelativeFolders(companyPath.company());
        Path input = normalized(companyPath.root().resolve(companyPath.company().folders().input()));
        validateOutputIsNotInput(companyPath, input, companyPath.company().folders().processed());
        validateOutputIsNotInput(companyPath, input, companyPath.company().folders().review());
        validateOutputIsNotInput(companyPath, input, companyPath.company().folders().originals());
        validateOutputIsNotInput(companyPath, input, companyPath.company().folders().logs());
        validateOutputIsNotInput(companyPath, input, companyPath.company().folders().cancelled());
        if (!Files.isDirectory(input) || !Files.isReadable(input)) {
            throw new IllegalArgumentException("pasta de entrada invalida para empresa "
                    + companyPath.company().id() + ": " + input);
        }
        String previous = inputFolders.putIfAbsent(input, companyPath.company().id());
        if (previous != null) {
            throw new IllegalArgumentException("pasta de entrada duplicada entre empresas "
                    + previous + " e " + companyPath.company().id() + ": " + input);
        }
    }

    private static void validateOutputIsNotInput(ResolvedCompanyPath companyPath, Path input, String outputFolder) {
        Path output = normalized(companyPath.root().resolve(outputFolder));
        if (input.equals(output)) {
            throw new IllegalArgumentException("pasta de saida coincide com entrada para empresa "
                    + companyPath.company().id() + ": " + outputFolder);
        }
    }

    private static void validateSafeRelativeFolders(CompanyConfig company) {
        validateSafeRelativeFolder(company, "entrada", company.folders().input());
        validateSafeRelativeFolder(company, "processados", company.folders().processed());
        validateSafeRelativeFolder(company, "revisar", company.folders().review());
        validateSafeRelativeFolder(company, "originais", company.folders().originals());
        validateSafeRelativeFolder(company, "logs", company.folders().logs());
        validateSafeRelativeFolder(company, "canceladas", company.folders().cancelled());
        validateSafeRelativeFolder(company, "ledger", company.folders().ledger());
    }

    private static void validateSafeRelativeFolder(CompanyConfig company, String field, String value) {
        Path path = Path.of(value == null ? "" : value);
        if (!path.isAbsolute() && normalized(company.basePath().resolve(path)).startsWith(normalized(company.basePath()))) {
            return;
        }
        throw new IllegalArgumentException("caminho inseguro em " + field + " para empresa "
                + company.id() + ": " + value);
    }

    private static void validateBackendRoot(CompanyRegistry registry) {
        if (registry.backendRoot().isEmpty()) {
            return;
        }
        Path backendRoot = normalized(registry.backendRoot().orElseThrow());
        for (CompanyConfig company : registry.companies()) {
            Path restRoot = normalized(company.basePath());
            if (backendRoot.equals(restRoot) || backendRoot.startsWith(restRoot)) {
                throw new IllegalArgumentException("backendRoot nao pode ficar dentro da REST da empresa "
                        + company.id() + ": " + backendRoot);
            }
        }
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static boolean isValidCnpj(String value) {
        String digits = digits(value);
        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 12) == Character.digit(digits.charAt(12), 10)
                && checkDigit(digits, 13) == Character.digit(digits.charAt(13), 10);
    }

    private static String digits(String value) {
        return value == null ? "" : NON_DIGITS.matcher(value).replaceAll("");
    }

    private static String destinationTaxIdKey(CompanyConfig company) {
        String taxId = digits(company.customerTaxId());
        return company.importedMonth()
                .map(month -> taxId + "|" + month)
                .orElse(taxId);
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
}
