package br.com.nfse.renomeador.config;

import br.com.nfse.renomeador.text.TextNormalizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record CompanyRouteDirectory(
        List<ResolvedCompanyPath> monitoredPaths,
        List<ResolvedCompanyPath> activePaths,
        Set<String> knownCustomerTaxIds
) {
    public CompanyRouteDirectory {
        monitoredPaths = List.copyOf(monitoredPaths);
        activePaths = List.copyOf(activePaths);
        knownCustomerTaxIds = Set.copyOf(knownCustomerTaxIds);
    }

    public static CompanyRouteDirectory single(ResolvedCompanyPath companyPath) {
        String taxId = TextNormalizer.digitsOnly(companyPath.company().customerTaxId());
        return new CompanyRouteDirectory(List.of(companyPath), List.of(companyPath), Set.of(taxId));
    }

    public static CompanyRouteDirectory from(CompanyRegistry registry, List<ResolvedCompanyPath> monitoredPaths,
                                             List<ResolvedCompanyPath> activePaths) {
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
        return new CompanyRouteDirectory(monitoredPaths, activePaths, known);
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
}
