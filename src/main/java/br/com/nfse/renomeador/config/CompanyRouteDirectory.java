package br.com.nfse.renomeador.config;

import br.com.nfse.renomeador.text.TextNormalizer;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record CompanyRouteDirectory(
        List<ResolvedCompanyPath> monitoredPaths,
        List<ResolvedCompanyPath> activePaths,
        Set<String> knownCustomerTaxIds,
        Path backendRoot
) {
    public CompanyRouteDirectory(List<ResolvedCompanyPath> monitoredPaths,
                                 List<ResolvedCompanyPath> activePaths,
                                 Set<String> knownCustomerTaxIds) {
        this(monitoredPaths, activePaths, knownCustomerTaxIds, defaultBackendRoot(monitoredPaths));
    }

    public CompanyRouteDirectory {
        monitoredPaths = List.copyOf(monitoredPaths);
        activePaths = List.copyOf(activePaths);
        knownCustomerTaxIds = Set.copyOf(knownCustomerTaxIds);
        backendRoot = backendRoot == null ? defaultBackendRoot(monitoredPaths) : backendRoot;
    }

    public static CompanyRouteDirectory single(ResolvedCompanyPath companyPath) {
        String taxId = TextNormalizer.digitsOnly(companyPath.company().customerTaxId());
        return new CompanyRouteDirectory(List.of(companyPath), List.of(companyPath), Set.of(taxId));
    }

    public static CompanyRouteDirectory from(CompanyRegistry registry, List<ResolvedCompanyPath> monitoredPaths,
                                             List<ResolvedCompanyPath> activePaths) {
        return from(registry, monitoredPaths, activePaths, defaultBackendRoot(monitoredPaths));
    }

    public static CompanyRouteDirectory from(CompanyRegistry registry, List<ResolvedCompanyPath> monitoredPaths,
                                             List<ResolvedCompanyPath> activePaths, Path backendRoot) {
        Set<String> known = new HashSet<>();
        for (CompanyConfig company : registry.companies()) {
            if (company.sourceOnly()) {
                continue;
            }
            String taxId = TextNormalizer.digitsOnly(company.customerTaxId());
            if (!taxId.isBlank()) {
                known.add(taxId);
            }
        }
        return new CompanyRouteDirectory(monitoredPaths, activePaths, known, backendRoot);
    }

    public Optional<ResolvedCompanyPath> activePathForCustomerTaxId(String taxId) {
        String digits = TextNormalizer.digitsOnly(taxId);
        if (digits.isBlank()) {
            return Optional.empty();
        }
        Map<String, ResolvedCompanyPath> byTaxId = new HashMap<>();
        for (ResolvedCompanyPath path : activePaths) {
            if (path.company().sourceOnly()) {
                continue;
            }
            byTaxId.putIfAbsent(TextNormalizer.digitsOnly(path.company().customerTaxId()), path);
        }
        return Optional.ofNullable(byTaxId.get(digits));
    }

    public Optional<ResolvedCompanyPath> activePathForCustomerTaxIdAndMonth(String taxId, YearMonth month) {
        String digits = TextNormalizer.digitsOnly(taxId);
        if (digits.isBlank()) return Optional.empty();
        List<ResolvedCompanyPath> matches = activePaths.stream()
                .filter(p -> !p.company().sourceOnly())
                .filter(p -> digits.equals(TextNormalizer.digitsOnly(p.company().customerTaxId())))
                .toList();
        if (matches.isEmpty()) return Optional.empty();
        Optional<ResolvedCompanyPath> exactMatch = matches.stream()
                .filter(p -> p.month().map(month::equals).orElse(false))
                .findFirst();
        if (exactMatch.isPresent()) return exactMatch;
        boolean hasAnyMonthInfo = matches.stream().anyMatch(p -> p.month().isPresent());
        if (hasAnyMonthInfo) return Optional.empty();
        return Optional.of(matches.get(0));
    }

    public Optional<ResolvedCompanyPath> activePathForCompanyId(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Optional.empty();
        }
        for (ResolvedCompanyPath path : activePaths) {
            if (companyId.equals(path.company().id())) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    public boolean hasKnownCustomerTaxId(String taxId) {
        return knownCustomerTaxIds.contains(TextNormalizer.digitsOnly(taxId));
    }

    private static Path defaultBackendRoot(List<ResolvedCompanyPath> monitoredPaths) {
        if (monitoredPaths == null || monitoredPaths.isEmpty()) {
            return Path.of("backend").toAbsolutePath().normalize();
        }
        Path root = monitoredPaths.get(0).root().toAbsolutePath().normalize();
        if (monitoredPaths.size() == 1) {
            return root.resolve("backend").normalize();
        }
        Path parent = root.getParent();
        return (parent == null ? root : parent).resolve("backend").normalize();
    }
}
