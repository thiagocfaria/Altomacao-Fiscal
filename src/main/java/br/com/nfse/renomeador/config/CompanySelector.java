package br.com.nfse.renomeador.config;

import java.util.List;
import java.util.Optional;

public final class CompanySelector {
    public List<CompanyConfig> select(CompanyRegistry registry, Optional<String> requestedCompanyId) {
        if (requestedCompanyId.isEmpty()) {
            return registry.companies().stream()
                    .filter(CompanyConfig::enabled)
                    .toList();
        }

        String id = requestedCompanyId.orElseThrow();
        CompanyConfig company = registry.companyById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa nao encontrada: " + id));
        if (!company.enabled()) {
            throw new IllegalArgumentException("Empresa desabilitada: " + id);
        }
        return List.of(company);
    }
}
