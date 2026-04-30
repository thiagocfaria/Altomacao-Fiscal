package br.com.nfse.renomeador.config;

import java.util.List;
import java.util.Optional;

public record CompanyRegistry(List<CompanyConfig> companies) {
    public CompanyRegistry {
        companies = companies == null ? List.of() : List.copyOf(companies);
    }

    public Optional<CompanyConfig> companyById(String id) {
        return companies.stream()
                .filter(company -> company.id().equals(id))
                .findFirst();
    }
}
