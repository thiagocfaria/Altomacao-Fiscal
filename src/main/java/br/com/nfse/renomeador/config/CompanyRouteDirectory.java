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

public final class CompanyRouteDirectory {
    private final List<ResolvedCompanyPath> monitoredPaths;
    private final List<ResolvedCompanyPath> activePaths;
    private final Set<String> knownCustomerTaxIds;
    private final Path backendRoot;
    private final Map<String, ResolvedCompanyPath> pathByCustomerTaxId;
    private final Map<String, ResolvedCompanyPath> pathByCustomerTaxIdAndMonth;
    private final Map<String, ResolvedCompanyPath> pathByCompanyId;

    public CompanyRouteDirectory(List<ResolvedCompanyPath> monitoredPaths,
                                 List<ResolvedCompanyPath> activePaths,
                                 Set<String> knownCustomerTaxIds) {
        this(monitoredPaths, activePaths, knownCustomerTaxIds, defaultBackendRoot(monitoredPaths));
    }

    public CompanyRouteDirectory(List<ResolvedCompanyPath> monitoredPaths,
                                 List<ResolvedCompanyPath> activePaths,
                                 Set<String> knownCustomerTaxIds,
                                 Path backendRoot) {
        this.monitoredPaths = List.copyOf(monitoredPaths);
        this.activePaths = List.copyOf(activePaths);
        this.knownCustomerTaxIds = Set.copyOf(knownCustomerTaxIds);
        this.backendRoot = backendRoot == null ? defaultBackendRoot(monitoredPaths) : backendRoot;
        this.pathByCustomerTaxId = buildTaxIdIndex(this.activePaths);
        this.pathByCustomerTaxIdAndMonth = buildTaxIdMonthIndex(this.activePaths);
        this.pathByCompanyId = buildCompanyIdIndex(this.activePaths);
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

    public List<ResolvedCompanyPath> monitoredPaths() {
        return monitoredPaths;
    }

    public List<ResolvedCompanyPath> activePaths() {
        return activePaths;
    }

    public Set<String> knownCustomerTaxIds() {
        return knownCustomerTaxIds;
    }

    public Path backendRoot() {
        return backendRoot;
    }

    public Optional<ResolvedCompanyPath> activePathForCustomerTaxId(String taxId) {
        String digits = TextNormalizer.digitsOnly(taxId);
        if (digits.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pathByCustomerTaxId.get(digits));
    }

    public Optional<ResolvedCompanyPath> activePathForCustomerTaxIdAndMonth(String taxId, YearMonth month) {
        String digits = TextNormalizer.digitsOnly(taxId);
        if (digits.isBlank()) {
            return Optional.empty();
        }
        ResolvedCompanyPath exact = pathByCustomerTaxIdAndMonth.get(monthKey(digits, month));
        if (exact != null) {
            return Optional.of(exact);
        }
        boolean hasAnyMonthInfo = pathByCustomerTaxIdAndMonth.keySet().stream()
                .anyMatch(key -> key.startsWith(digits + "|"));
        if (hasAnyMonthInfo) {
            return Optional.empty();
        }
        return Optional.ofNullable(pathByCustomerTaxId.get(digits));
    }

    public Optional<ResolvedCompanyPath> activePathForCompanyId(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pathByCompanyId.get(companyId));
    }

    public boolean hasKnownCustomerTaxId(String taxId) {
        return knownCustomerTaxIds.contains(TextNormalizer.digitsOnly(taxId));
    }

    private static Map<String, ResolvedCompanyPath> buildTaxIdIndex(List<ResolvedCompanyPath> activePaths) {
        Map<String, ResolvedCompanyPath> index = new HashMap<>();
        for (ResolvedCompanyPath path : activePaths) {
            if (path.company().sourceOnly()) {
                continue;
            }
            String taxId = TextNormalizer.digitsOnly(path.company().customerTaxId());
            if (!taxId.isBlank()) {
                index.putIfAbsent(taxId, path);
            }
        }
        return Map.copyOf(index);
    }

    private static Map<String, ResolvedCompanyPath> buildTaxIdMonthIndex(List<ResolvedCompanyPath> activePaths) {
        Map<String, ResolvedCompanyPath> index = new HashMap<>();
        for (ResolvedCompanyPath path : activePaths) {
            if (path.company().sourceOnly() || path.month().isEmpty()) {
                continue;
            }
            String taxId = TextNormalizer.digitsOnly(path.company().customerTaxId());
            if (!taxId.isBlank()) {
                index.putIfAbsent(monthKey(taxId, path.month().orElseThrow()), path);
            }
        }
        return Map.copyOf(index);
    }

    private static Map<String, ResolvedCompanyPath> buildCompanyIdIndex(List<ResolvedCompanyPath> activePaths) {
        Map<String, ResolvedCompanyPath> index = new HashMap<>();
        for (ResolvedCompanyPath path : activePaths) {
            String id = path.company().id();
            if (id != null && !id.isBlank()) {
                index.putIfAbsent(id, path);
            }
        }
        return Map.copyOf(index);
    }

    private static String monthKey(String taxId, YearMonth month) {
        return taxId + "|" + month;
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
